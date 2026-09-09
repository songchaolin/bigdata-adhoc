// Adhoc 指标大盘前端：on-the-fly 调 /api/metrics/*，ECharts 渲染，30s 自动刷新。
// API 基址用相对路径 ../api/metrics，自动适配 context-path（/dashboard/ -> /api 或 /ctx/dashboard/ -> /ctx/api）。
// 时间筛选用内嵌 flatpickr（支持时分，离线自包含），SQL 控制台按需触发（不随 30s 自动刷新）。
// 访问鉴权：服务端配 adhoc.dashboard.access-token 后，请求须带 X-Dashboard-Token 头。
//   首次访问通过 URL ?token=xxx 携带，前端取出写 localStorage 并清掉 URL 参数（不残留历史/地址栏），
//   之后所有静态资源与 fetch 请求统一从 localStorage 取 token 塞 header。token 缺失时显示遮罩不发请求。
(function () {
    'use strict';
    var BASE = '../api/metrics';
    var REFRESH_MS = 30000;
    var TOKEN_KEY = 'adhoc_dashboard_token';
    var TOKEN_HEADER = 'X-Dashboard-Token';
    var charts = {};
    var timer = null;
    var fpStart = null, fpEnd = null;

    // ---------- 访问令牌 ----------
    /** 取 token：优先 URL ?token=（首次带参访问），取到则写 localStorage 并清 URL 参数；URL 无则读 localStorage。
     *  返回空串表示无 token（URL 无 + localStorage 无），调用方据此显示遮罩、不发请求。 */
    function getToken() {
        var fromUrl = null;
        try {
            var u = new URL(window.location.href);
            fromUrl = u.searchParams.get('token');
        } catch (e) { /* URL API 不可用时忽略 */ }
        if (fromUrl) {
            try { localStorage.setItem(TOKEN_KEY, fromUrl); } catch (e) { /* localStorage 禁用时仅本次会话用 URL 值 */ }
            // 清掉 URL 中的 token，避免残留浏览器历史/地址栏/Referer 泄露
            try {
                u.searchParams.delete('token');
                window.history.replaceState({}, document.title, u.toString());
            } catch (e) { /* replaceState 失败不影响功能 */ }
            return fromUrl;
        }
        try { return localStorage.getItem(TOKEN_KEY) || ''; } catch (e) { return ''; }
    }
    /** 统一鉴权请求头（三处 fetch 复用）。token 为空时也返回头，由后端决定放行/拒绝（配置空时放行）。 */
    function authHeaders() {
        var h = {};
        var t = getToken();
        if (t) h[TOKEN_HEADER] = t;
        return h;
    }
    /** 401 处理：清掉失效 token，显示遮罩引导重新带 token 访问。 */
    function onTokenInvalid() {
        try { localStorage.removeItem(TOKEN_KEY); } catch (e) { /* ignore */ }
        showTokenGate('访问令牌失效或缺失，请重新通过带 token 的链接访问');
    }
    /** 显示 token 缺失遮罩（msg 可空）。 */
    function showTokenGate(msg) {
        var g = el('tokenGate');
        if (!g) return;
        if (msg) { var m = g.querySelector('.tg-msg'); if (m) m.textContent = msg; }
        g.style.display = '';
    }

    // ---------- 穿透下钻状态 ----------
    // 时长桶 -> 耗时区间，与后端 selectDurationStats 分桶边界逐字对齐：[0,1000)/[1000,10000)/[10000,60000)/[60000,∞)
    var BUCKET_RANGE = { '0-1s': [0, 1000], '1-10s': [1000, 10000], '10-60s': [10000, 60000], '>60s': [60000, null] };
    var drillState = { current: 1, size: 20, filter: {}, total: 0 };   // 明细分页表
    var drawerState = { jobId: null, logOffset: 0, logDone: false, logTimer: null };  // Job 详情抽屉

    // ---------- 工具 ----------
    function el(id) { return document.getElementById(id); }
    function setStatus(cls, title) {
        var s = el('status');
        s.className = 'status-dot ' + cls;
        s.title = title || '';
    }
    function fmtDur(ms) {
        if (ms == null) return '-';
        var n = Number(ms);
        if (!isFinite(n)) return '-';
        if (n < 1000) return Math.round(n) + ' ms';
        if (n < 60000) return (n / 1000).toFixed(1) + ' s';
        var m = Math.floor(n / 60000), s = Math.round((n % 60000) / 1000);
        return m + 'm ' + s + 's';
    }
    function fmtNum(n) { return n == null ? '-' : Number(n).toLocaleString(); }
    function fmtTime(d) {
        function p(x) { return x < 10 ? '0' + x : x; }
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    /** 日期/epoch -> 'MM-dd HH:mm:ss'（兼容 Date、epoch 毫秒、ISO 字符串）。 */
    function fmtDate(d) {
        if (d == null) return '-';
        var dt = (d instanceof Date) ? d : new Date(d);
        if (isNaN(dt.getTime())) return String(d);
        function p(x) { return x < 10 ? '0' + x : x; }
        return p(dt.getMonth() + 1) + '-' + p(dt.getDate()) + ' ' + p(dt.getHours()) + ':' + p(dt.getMinutes()) + ':' + p(dt.getSeconds());
    }
    function setStatusText(text) { el('lastUpdate').textContent = text; }
    /** 手动刷新按钮：加载中禁用 + 图标旋转，结束后恢复。 */
    function setRefresh(loading) {
        var b = el('refreshBtn');
        if (!b) return;
        b.disabled = loading;
        if (loading) b.classList.add('loading'); else b.classList.remove('loading');
    }
    /** 防抖：flatpickr 连续选日期+调时分会多次 onChange，合并成一次 load。 */
    var loadDebounced = (function () { var t; return function () { clearTimeout(t); t = setTimeout(load, 250); }; })();

    /** 构造时间筛选查询串：
     *  - 自定义区间 -> startMs/endMs（flatpickr 选中的 Date，含时分，end 取该分钟末尾）；
     *  - 分钟级预设（min5/min30）-> startMs/endMs（客户端 now - N 分钟；int hours 表达不了 <1h，走 epoch ms 路径，后端 resolveRange 同样支持）；
     *  - 小时/天预设（1/24/72/168）-> hours=N（服务端 now - Nh）。
     *  后端统一 resolveRange：自定义/分钟级用传入 epoch ms，小时级用服务端 now。 */
    function queryParams() {
        var v = el('windowSel').value;
        if (v === 'custom') {
            var sd = fpStart && fpStart.selectedDates[0];
            var ed = fpEnd && fpEnd.selectedDates[0];
            if (sd && ed) {
                var startMs = sd.getTime();
                var endMs = ed.getTime() + 59999;
                if (endMs > startMs) return 'startMs=' + startMs + '&endMs=' + endMs;
            }
            return 'hours=24'; // 区间未填全或倒序，退回 24h
        }
        if (v.indexOf('min') === 0) {            // 分钟级预设（< 1h），客户端 now 算 startMs/endMs
            var mins = parseInt(v.substring(3), 10);
            if (mins > 0) {
                var now = Date.now();
                return 'startMs=' + (now - mins * 60000) + '&endMs=' + now;
            }
        }
        return 'hours=' + (parseInt(v, 10) || 24);
    }
    /** 切换到自定义区间时预填 start=7天前 / end=现在（含当前时分），并显示/隐藏输入框。 */
    function syncRangeUI() {
        var custom = el('windowSel').value === 'custom';
        el('rangeWrap').style.display = custom ? '' : 'none';
        if (custom) {
            var now = new Date();
            var start = new Date(now.getTime() - 6 * 24 * 3600 * 1000);
            if (fpStart && !fpStart.selectedDates[0]) fpStart.setDate(start, false);
            if (fpEnd && !fpEnd.selectedDates[0]) fpEnd.setDate(now, false);
        }
    }

    function get(path) {
        return fetch(BASE + path, { headers: authHeaders() }).then(function (r) {
            if (r.status === 401) { onTokenInvalid(); throw new Error('token 失效或缺失'); }
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
        }).then(function (res) {
            // Result 框架：成功码=1，失败码!=0
            if (!res || res.code !== 1) throw new Error((res && res.msg) || '请求失败');
            return res.data;
        });
    }

    // ---------- ECharts 装配 ----------
    function chart(id) {
        if (!charts[id]) {
            var node = el(id);
            if (!node) return null;
            charts[id] = echarts.init(node);
        }
        return charts[id];
    }
    function pie(domId, title, map, opts) {
        var c = chart(domId);
        if (!c) return;
        var data = [];
        for (var k in map) if (map.hasOwnProperty(k)) data.push({ name: k || '(空)', value: Number(map[k]) });
        c.setOption({
            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
            legend: { bottom: 0, type: 'scroll' },
            color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4'],
            series: [{
                type: 'pie', radius: ['38%', '62%'], center: ['50%', '46%'],
                label: { formatter: '{b}\n{d}%', fontSize: 11 },
                data: data
            }]
        }, true);
    }
    function bar(domId, names, values, opts) {
        var c = chart(domId);
        if (!c) return;
        var horizontal = opts && opts.horizontal;
        var opt = {
            tooltip: { trigger: 'axis' },
            grid: { left: horizontal ? 90 : 30, right: 16, top: 16, bottom: 24 },
            xAxis: { type: horizontal ? 'value' : 'category', axisLabel: { fontSize: 11 } },
            yAxis: { type: horizontal ? 'category' : 'value', data: horizontal ? names : null, axisLabel: { fontSize: 11 } },
            series: [{ type: 'bar', barMaxWidth: 28, itemStyle: { borderRadius: horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0] }, data: values }]
        };
        if (!horizontal) opt.xAxis.data = names;
        c.setOption(opt, true);
    }
    function trend(domId, hours, submitted, finished) {
        var c = chart(domId);
        if (!c) return;
        c.setOption({
            tooltip: { trigger: 'axis' },
            legend: { data: ['提交', '完成'], top: 0, right: 8 },
            grid: { left: 36, right: 16, top: 30, bottom: 28 },
            xAxis: { type: 'category', boundaryGap: false, data: hours, axisLabel: { fontSize: 10, interval: 'auto' } },
            yAxis: { type: 'value', minInterval: 1 },
            series: [
                { name: '提交', type: 'line', smooth: true, showSymbol: false, areaStyle: { opacity: 0.15 }, data: submitted, color: '#5470c6' },
                { name: '完成', type: 'line', smooth: true, showSymbol: false, areaStyle: { opacity: 0.15 }, data: finished, color: '#91cc75' }
            ]
        }, true);
    }
    /** 分组柱状图：每实例两系列（Job 数 / Task 数）。实例 >6 时横向，Top1 在顶。 */
    function groupedBar(domId, rows) {
        var c = chart(domId);
        if (!c) return;
        var names = rows.map(function (r) { return r.instance || '(未分配)'; });
        var jobs = rows.map(function (r) { return Number(r.jobs) || 0; });
        var tasks = rows.map(function (r) { return Number(r.tasks) || 0; });
        var horizontal = names.length > 6;
        if (horizontal) { // 倒序使 Top1 显示在顶
            names.reverse(); jobs.reverse(); tasks.reverse();
        }
        c.setOption({
            tooltip: { trigger: 'axis' },
            legend: { data: ['Job 数', 'Task 数'], top: 0, right: 8 },
            grid: { left: horizontal ? 120 : 36, right: 20, top: 30, bottom: 28 },
            xAxis: {
                type: horizontal ? 'value' : 'category',
                data: horizontal ? null : names,
                axisLabel: { fontSize: 10, interval: 0, rotate: (!horizontal && names.length > 5) ? 35 : 0 }
            },
            yAxis: { type: horizontal ? 'category' : 'value', data: horizontal ? names : null, axisLabel: { fontSize: 10 } },
            series: [
                { name: 'Job 数', type: 'bar', barMaxWidth: 26, data: jobs, itemStyle: { borderRadius: horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0] }, color: '#5470c6' },
                { name: 'Task 数', type: 'bar', barMaxWidth: 26, data: tasks, itemStyle: { borderRadius: horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0] }, color: '#91cc75' }
            ]
        }, true);
    }

    // ---------- 渲染 ----------
    function renderOverview(o) {
        el('kJobTotal').textContent = fmtNum(o.jobTotal);
        el('kSuccessRate').textContent = (o.successRate != null ? o.successRate : 0) + '%';
        el('kAvgDur').textContent = fmtDur(o.avgDurationMs);
        el('kPending').textContent = fmtNum(o.pending);
        el('kRunning').textContent = fmtNum(o.running);
        el('kExecUp').textContent = o.executorUp + '/' + o.executorTotal;
        el('kServerUp').textContent = o.serverUp + '/' + o.serverTotal;
        pie('jobStatusPie', 'Job 状态', o.jobByStatus);
        pie('enginePie', '引擎', o.engineDistribution);
        var bk = o.durationBuckets || {};
        var bkeys = ['0-1s', '1-10s', '10-60s', '>60s'];
        bar('durBucketBar', bkeys, bkeys.map(function (k) { return Number(bk[k]) || 0; }));

        // 图表穿透：首次渲染后绑 click（_drillBound 防重复；setOption(notMerge) 刷新不丢事件）
        bindClick('jobStatusPie', function (p) { if (p && p.name) drillJobs({ status: p.name }); });
        bindClick('enginePie', function (p) { if (p && p.name) drillJobs({ engineType: p.name }); });
        bindClick('durBucketBar', function (p) {
            if (p && p.name && BUCKET_RANGE[p.name]) {
                var b = BUCKET_RANGE[p.name];
                drillJobs({ minDurationMs: b[0], maxDurationMs: b[1], _bucket: p.name });
            }
        });
    }

    function renderTrends(points) {
        var hours = points.map(function (p) { return p.hour; });
        trend('trendLine', hours,
            points.map(function (p) { return Number(p.submitted) || 0; }),
            points.map(function (p) { return Number(p.finished) || 0; }));
    }

    function renderTaskTrends(points) {
        var hours = points.map(function (p) { return p.hour; });
        trend('taskTrendLine', hours,
            points.map(function (p) { return Number(p.submitted) || 0; }),
            points.map(function (p) { return Number(p.finished) || 0; }));
    }

    function renderTaskFail(f) {
        var stage = f.byStage || [];
        bar('failStageBar',
            stage.map(function (r) { return r.name || '(空)'; }),
            stage.map(function (r) { return Number(r.cnt); }));
        var ec = (f.byErrorCode || []).slice().reverse(); // 横向柱倒序使 Top1 在顶
        bar('errorCodeBar',
            ec.map(function (r) { return r.name || '(空)'; }),
            ec.map(function (r) { return Number(r.cnt); }),
            { horizontal: true });
        pie('sqlTypePie', 'SQL 类型', toMap(f.bySqlType));
    }
    function toMap(list) {
        var m = {};
        (list || []).forEach(function (r) { m[r.name || '(空)'] = Number(r.cnt); });
        return m;
    }

    function renderExecutors(list) {
        var tb = el('execTable').querySelector('tbody');
        tb.innerHTML = '';
        (list || []).forEach(function (r) {
            var st = (r.status || '').toUpperCase();
            var hb = Number(r.heartbeatAgeSec) || 0;
            var tr = document.createElement('tr');
            tr.innerHTML =
                '<td class="mono">' + esc(r.instanceId || '') + '</td>' +
                '<td class="' + (st === 'UP' ? 'up' : 'down') + '">' + st + '</td>' +
                '<td>' + (r.accepting === 1 ? '是' : '否') + '</td>' +
                '<td>' + (r.runningTasks || 0) + '/' + (r.maxConcurrent || 0) + '</td>' +
                '<td>' + (r.utilizationPct != null ? r.utilizationPct : 0) + '%</td>' +
                '<td class="' + hbClass(st, hb) + '">' + fmtNum(r.heartbeatAgeSec) + '</td>' +
                '<td>' + (r.cpuUsagePct != null ? r.cpuUsagePct : 0) + '</td>' +
                '<td>' + (r.memoryUsagePct != null ? r.memoryUsagePct : 0) + '</td>' +
                '<td>' + (r.loadScore != null ? r.loadScore : 0) + '</td>';
            tb.appendChild(tr);
        });
        if (!(list && list.length)) tb.innerHTML = '<tr><td colspan="9" style="text-align:center;color:#8492a6">暂无在线 executor</td></tr>';
    }

    function renderServers(list) {
        var tb = el('serverTable').querySelector('tbody');
        tb.innerHTML = '';
        (list || []).forEach(function (r) {
            var st = (r.status || '').toUpperCase();
            var hb = Number(r.heartbeatAgeSec) || 0;
            var tr = document.createElement('tr');
            tr.innerHTML =
                '<td class="mono">' + esc(r.instanceId || '') + '</td>' +
                '<td class="' + (st === 'UP' ? 'up' : 'down') + '">' + st + '</td>' +
                '<td>' + (r.accepting === 1 ? '是' : '否') + '</td>' +
                '<td>' + (r.activeJobs != null ? r.activeJobs : 0) + '</td>' +
                '<td class="' + hbClass(st, hb) + '">' + fmtNum(r.heartbeatAgeSec) + '</td>' +
                '<td class="mono">' + esc(r.version || '-') + '</td>';
            tb.appendChild(tr);
        });
        if (!(list && list.length)) tb.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#8492a6">暂无在线 server</td></tr>';
    }

    /** 心跳新鲜度着色：UP 但 >30s 标橙（stale）；DOWN 直接红。 */
    function hbClass(status, hb) {
        if (status !== 'UP') return 'down';
        return hb > 30 ? 'stale' : '';
    }

    function renderTopN(t) {
        var stb = el('slowTable').querySelector('tbody');
        stb.innerHTML = '';
        (t.slowJobs || []).forEach(function (r) {
            var tr = document.createElement('tr');
            tr.className = 'clickable';
            tr.title = '点击查看 Job 详情';
            tr.innerHTML =
                '<td class="mono" title="' + esc(r.jobId || '') + '">' + esc(shortId(r.jobId)) + '</td>' +
                '<td>' + esc(r.userName || r.userId || '-') + '</td>' +
                '<td>' + esc(r.engineType || '-') + '</td>' +
                '<td>' + esc(r.status || '-') + '</td>' +
                '<td>' + fmtDur(r.durationMs) + '</td>';
            tr.onclick = (function (jobId) { return function () { if (jobId) openDrawer(jobId); }; })(r.jobId);
            stb.appendChild(tr);
        });
        if (!(t.slowJobs && t.slowJobs.length)) stb.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#8492a6">无数据</td></tr>';

        var utb = el('userTable').querySelector('tbody');
        utb.innerHTML = '';
        (t.activeUsers || []).forEach(function (r) {
            var tr = document.createElement('tr');
            tr.innerHTML =
                '<td>' + esc(r.userName || '-') + '</td>' +
                '<td class="mono">' + esc(r.userId || '-') + '</td>' +
                '<td>' + fmtNum(r.cnt) + '</td>';
            utb.appendChild(tr);
        });
        if (!(t.activeUsers && t.activeUsers.length)) utb.innerHTML = '<tr><td colspan="3" style="text-align:center;color:#8492a6">无数据</td></tr>';
    }

    /** 每实例负载：server / executor 两图（时间窗内 Job 数 + Task 数分组柱）。 */
    function renderInstanceLoad(d) {
        groupedBar('serverLoadBar', (d && d.serverLoad) || []);
        groupedBar('executorLoadBar', (d && d.executorLoad) || []);
    }

    // ==================== JVM 监控（角色开关 + 实例勾选联动 + 时间曲线） ====================
    // 数据源：adhoc_jvm_metric_sample 采样历史（5s）。GET /api/metrics/jvm-series 拉时序，按实例画线。
    // 状态：raw=实例列表（供 chip）；selected[key]=勾选；roleServer/roleExecutor=角色开关；windowMin=JVM按钮设定的分钟；series=时序。
    // followTop=true：JVM 时序跟随顶部时间窗（默认，与业务图表同口径，选7d则JVM也展示最近7天）；
    // followTop=false：用户点了JVM区按钮做局部覆盖，仅JVM用该分钟窗，业务图表仍跟顶部。
    var jvmState = { raw: { servers: [], executors: [] }, selected: {}, roleServer: true, roleExecutor: true, windowMin: 60, followTop: true, series: {} };

    /** 入口：load() 每 30s 调。存实例列表 -> 同步 chip -> 拉时序重绘 16 图（保留勾选/窗口态）。 */
    function renderJvm(executors, servers) {
        jvmState.raw.servers = servers || [];
        jvmState.raw.executors = executors || [];
        syncJvmChips();
        fetchJvmSeries();
    }

    /** 候选实例全集：server[S] + executor[E]，每项含 key/label/role/data。key=角色:instanceId 去重。
     *  label=纯 IP（chip 配 [S]/[E] 标签显示）；rolePrefix=server/executor 供 legend 拼 "server-ip" 便于识别。 */
    function jvmCandidates() {
        var list = [];
        (jvmState.raw.servers || []).forEach(function (s) {
            var ip = s.host || s.instanceId || '-';
            list.push({ role: 'S', key: 'S:' + (s.instanceId || s.host), ip: ip, label: ip, rolePrefix: 'server', data: s });
        });
        (jvmState.raw.executors || []).forEach(function (e) {
            var ip = e.host || e.instanceId || '-';
            list.push({ role: 'E', key: 'E:' + (e.instanceId || e.host), ip: ip, label: ip, rolePrefix: 'executor', data: e });
        });
        return list;
    }
    function roleOn(role) { return role === 'S' ? jvmState.roleServer : jvmState.roleExecutor; }
    /** chip 是否生效：角色开（隐藏的 chip 不参与图）。 */
    function chipActive(c) { return roleOn(c.role) && jvmState.selected[c.key] !== false; }

    /** 同步 chip 列表：实例集变化才重建 DOM（防 30s 刷新丢焦点/闪烁），否则只刷可见性+勾选态。 */
    function syncJvmChips() {
        var box = el('jvmInstances');
        if (!box) return;
        var cands = jvmCandidates();
        var sig = cands.map(function (c) { return c.key; }).join('|');
        if (box.dataset.sig !== sig) {
            box.innerHTML = '';
            cands.forEach(function (c) {
                var lab = document.createElement('label');
                lab.className = 'jvm-chip role-' + c.role.toLowerCase();
                lab.dataset.key = c.key;
                lab.title = c.data.instanceId || c.label;
                var cb = document.createElement('input');
                cb.type = 'checkbox';
                cb.checked = jvmState.selected[c.key] !== false;
                cb.addEventListener('change', function () {
                    jvmState.selected[c.key] = cb.checked;
                    fetchJvmSeries();
                });
                lab.appendChild(cb);
                var tag = document.createElement('span');
                tag.className = 'jvm-chip-tag';
                tag.textContent = c.role;
                lab.appendChild(tag);
                var name = document.createElement('span');
                name.textContent = c.label;
                lab.appendChild(name);
                box.appendChild(lab);
            });
            box.dataset.sig = sig;
        }
        // 刷可见性 + 勾选态（角色开关或数据刷新后）
        var byKey = {};
        for (var i = 0; i < box.children.length; i++) byKey[box.children[i].dataset.key] = box.children[i];
        cands.forEach(function (c) {
            var lab = byKey[c.key];
            if (!lab) return;
            lab.style.display = roleOn(c.role) ? '' : 'none';
            lab.querySelector('input').checked = jvmState.selected[c.key] !== false;
        });
    }

    /** 选中实例的 instanceId 列表（去 role 前缀），供 series 查询。 */
    function jvmActiveInstanceIds() {
        var ids = [];
        jvmCandidates().forEach(function (c) {
            if (chipActive(c) && c.data.instanceId) ids.push(c.data.instanceId);
        });
        return ids;
    }

    /** instanceId -> 显示标签（供曲线 legend）：rolePrefix-ip，如 server-10.0.0.1，便于区分钟角色。 */
    function jvmLabelMap() {
        var m = {};
        jvmCandidates().forEach(function (c) {
            if (c.data.instanceId) m[c.data.instanceId] = c.rolePrefix + '-' + c.ip;
        });
        return m;
    }

    /** 拉时间曲线：GET /api/metrics/jvm-series?instanceIds=...&startMs/endMs 或 &minutes。空选中 -> 清空重绘。
     *  followTop=true（默认）：与业务图表同口径，把顶部时间窗解析成 startMs/endMs 传给后端
     *    （选7d则JVM也展示最近7天；custom 自定义区间同样透传绝对时间）；
     *  followTop=false：仅JVM区用按钮设定的 minutes=windowMin，做局部覆盖。后端自定义区间优先于 minutes。 */
    function fetchJvmSeries() {
        var ids = jvmActiveInstanceIds();
        if (!ids.length) { jvmState.series = {}; renderJvmCharts(); return; }
        var q = 'instanceIds=' + encodeURIComponent(ids.join(','));
        if (jvmState.followTop) {
            var range = topWindowRange();   // 顶部窗口 -> {startMs,endMs}，统一绝对时间，规避后端 /jvm-series 无 hours 参数
            q += '&startMs=' + range.startMs + '&endMs=' + range.endMs;
        } else {
            q += '&minutes=' + jvmState.windowMin;
        }
        fetch(BASE + '/jvm-series?' + q, { headers: authHeaders() })
            .then(function (r) {
                if (r.status === 401) { onTokenInvalid(); throw new Error('token'); }
                return r.json();
            })
            .then(function (resp) {
                jvmState.series = (resp && resp.code === 1 && resp.data) ? resp.data : {};
                renderJvmCharts();
            }).catch(function () { jvmState.series = {}; renderJvmCharts(); });
    }

    /** 顶部时间窗 -> {startMs,endMs} 绝对区间（与 queryParams() 同口径，但输出 epoch ms 供 /jvm-series，因其不接收 hours）：
     *  - 自定义区间 -> flatpickr 选中值，end 取该分钟末尾；
     *  - 分钟级预设 min5/min30 -> 客户端 now - N 分钟；
     *  - 小时/天预设 1/24/72/168 -> 客户端 now - N 小时；
     *  - 任一解析失败 -> 回退近 24 小时。 */
    function topWindowRange() {
        var v = el('windowSel').value;
        var now = Date.now();
        if (v === 'custom') {
            var sd = fpStart && fpStart.selectedDates[0];
            var ed = fpEnd && fpEnd.selectedDates[0];
            if (sd && ed) {
                var startMs = sd.getTime();
                var endMs = ed.getTime() + 59999;
                if (endMs > startMs) return { startMs: startMs, endMs: endMs };
            }
            return { startMs: now - 24 * 3600000, endMs: now };
        }
        if (v.indexOf('min') === 0) {
            var mins = parseInt(v.substring(3), 10);
            if (mins > 0) return { startMs: now - mins * 60000, endMs: now };
        }
        var hours = parseInt(v, 10);
        if (hours > 0) return { startMs: now - hours * 3600000, endMs: now };
        return { startMs: now - 24 * 3600000, endMs: now };
    }

    /** 顶部时间窗 -> JVM 按钮分钟数映射（顶部为主控时高亮对应JVM按钮，给用户视觉反馈）。
     *  顶部 min5/min30/1/24/72/168 对应 5/30/60/1440/4320/10080 分钟；custom 为绝对区间无精确按钮 -> 取消高亮。 */
    var TOP_TO_JVM_MIN = { 'min5': 5, 'min30': 30, '1': 60, '24': 1440, '72': 4320, '168': 10080 };
    function syncJvmBtnByTopWindow() {
        var v = el('windowSel').value;
        var min = TOP_TO_JVM_MIN[v];
        document.querySelectorAll('.jvm-window-btn').forEach(function (b) {
            b.classList.toggle('active', min != null && parseInt(b.dataset.min, 10) === min);
        });
    }

    /** 重绘 16 张时间曲线（X=time epoch ms，每选中实例一条线）。空选中或无采样显占位标题。 */
    function renderJvmCharts() {
        var labels = jvmLabelMap();
        var instances = Object.keys(jvmState.series);

        // 全局最新采样时间（所有选中实例所有点里最大的 ts）：空数据时用于占位提示，
        // 让用户区分"该时间窗内无数据（服务停过/采样刚开始/窗口太窄）"vs"功能失效"，而非干瘪的"暂无采样数据"。
        var latestTs = 0;
        instances.forEach(function (id) {
            var pts = jvmState.series[id] || [];
            for (var i = 0; i < pts.length; i++) {
                if (pts[i] && pts[i].ts != null && pts[i].ts > latestTs) latestTs = pts[i].ts;
            }
        });

        /** 取指标 getter -> 每实例一条 line；data=[ts,value] 绝对时间，ECharts time 轴对齐（无并集留空问题）。
         *  sampling=lttb + large：7d 窗口单实例约 12 万点，降采样保形 + 大数据模式保性能。 */
        function series(getter) {
            return instances.map(function (id) {
                var pts = jvmState.series[id] || [];
                return {
                    name: labels[id] || id, type: 'line', showSymbol: false, smooth: false,
                    sampling: 'lttb', large: true,
                    data: pts.map(function (p) {
                        return (p && p.ts != null) ? [p.ts, getter(p)] : null;
                    }).filter(function (x) { return x !== null; })
                };
            });
        }
        /** 累计计数/耗时 -> 当下速率：相邻采样差分 ÷ 时间差 × scale。
         *  - count 速率（次/分）：scale=60000，getter=youngGcCount/fullGcCount（累计次数，JVM 启动至今）。
         *  - 间隔 GC 占比（%）：scale=100，getter=gcTimeMs（累计 GC 耗时 ms）；= 近一采样间隔内 GC 占用时间比例。
         *  处理：①首点无前值 -> 跳过；②getter 任一为 null -> 跳过；③dv<0（JVM 重启计数器重置）或 dtMs<=0（同瞬）-> 跳过该点，后续 post-restart 继续正确递增。
         *  为什么前端算：sample 表存累计原始值（事实留底），速率是派生展示值，不动后端/DTO/采集。 */
        function diffSeries(getter, scale) {
            return instances.map(function (id) {
                var pts = jvmState.series[id] || [];
                var data = [];
                for (var i = 1; i < pts.length; i++) {
                    var p = pts[i], prev = pts[i - 1];
                    if (!p || p.ts == null || !prev || prev.ts == null) continue;
                    var cv = getter(p), pv = getter(prev);
                    if (cv == null || pv == null) continue;
                    var dv = cv - pv, dtMs = p.ts - prev.ts;
                    if (dtMs <= 0 || dv < 0) continue;
                    data.push([p.ts, +(dv / dtMs * scale).toFixed(2)]);
                }
                return {
                    name: labels[id] || id, type: 'line', showSymbol: false, smooth: false,
                    sampling: 'lttb', large: true, data: data
                };
            });
        }
        function lineOpt(s, yMax, unit) {
            var suffix = unit ? (' ' + unit) : '';
            return {
                tooltip: { trigger: 'axis', valueFormatter: function (v) { return (v == null ? '-' : v) + suffix; } },
                legend: { top: 0, right: 8, type: 'scroll', textStyle: { fontSize: 11 } },
                grid: { left: 48, right: 24, top: 30, bottom: 40 },
                xAxis: { type: 'time',
                    axisLabel: { fontSize: 10, color: '#4a5568', formatter: function (v) { return fmtClock(v); } } },
                yAxis: { type: 'value', max: yMax == null ? null : yMax,
                    axisLabel: { fontSize: 11, color: '#4a5568' },
                    splitLine: { lineStyle: { color: '#eef2f7' } } },
                series: s
            };
        }
        function apply(id, opt) {
            var c = chart(id);
            if (!c) return;
            if (!instances.length || !opt.series.length || !opt.series[0].data.length) {
                c.clear();
                // 有历史采样但不在当前时间窗 -> 提示最近采样时间，让用户知道是窗口问题而非功能失效；
                // 完全无采样 -> 仅显"暂无采样数据"。
                var msg = latestTs > 0
                    ? '该时间窗内无采样数据\n最近采样：' + fmtDate(latestTs) + '\n（尝试扩大时间窗）'
                    : '暂无采样数据';
                c.setOption({ title: { text: msg, left: 'center', top: 'center', textStyle: { color: '#8492a6', fontSize: 13, lineHeight: 20 } } });
                return;
            }
            c.setOption(opt, true);
        }

        apply('jvmCpu', lineOpt(series(function (p) { return p.cpuUsagePct; }), 100));
        apply('jvmSysCpu', lineOpt(series(function (p) { return p.systemCpuUsagePct; }), 100));
        apply('jvmLoad', lineOpt(series(function (p) { return p.systemLoadAvg; })));
        apply('jvmPhysMem', lineOpt(series(function (p) { return p.physMemUsedPct; }), 100));
        apply('jvmHeap', lineOpt(series(function (p) { return p.heapUsedMb; })));
        apply('jvmHeapPct', lineOpt(series(function (p) { return p.heapUsedPct; }), 100));
        apply('jvmNonHeap', lineOpt(series(function (p) { return p.nonHeapUsedMb; })));
        apply('jvmEden', lineOpt(series(function (p) { return p.edenUsedMb; })));
        apply('jvmOld', lineOpt(series(function (p) { return p.oldUsedMb; })));
        apply('jvmYoungGc', lineOpt(diffSeries(function (p) { return p.youngGcCount; }, 60000), null, '次/分'));
        apply('jvmFullGc', lineOpt(diffSeries(function (p) { return p.fullGcCount; }, 60000), null, '次/分'));
        apply('jvmGc', lineOpt(diffSeries(function (p) { return p.gcTimeMs; }, 100), 100, '%'));
        apply('jvmThread', lineOpt(series(function (p) { return p.threadCount; })));
        apply('jvmDaemon', lineOpt(series(function (p) { return p.daemonThreadCount; })));
        apply('jvmDirect', lineOpt(series(function (p) { return p.directBufferUsedMb; })));
        apply('jvmClass', lineOpt(series(function (p) { return p.loadedClassCount; })));
    }

    /** 时间轴标签：epoch ms -> "HH:mm"（紧凑，秒级太密）。 */
    function fmtClock(ms) {
        var d = new Date(ms);
        if (isNaN(d.getTime())) return '';
        return pad2(d.getHours()) + ':' + pad2(d.getMinutes());
    }
    function pad2(n) { return n < 10 ? '0' + n : '' + n; }


    /** SQL 控制台：按需 POST /sql，渲染结果表。不走 30s 自动刷新。 */
    function runSql() {
        var sql = el('sqlInput').value;
        if (!sql || !sql.trim()) { setSqlMeta('请输入 SQL', true); return; }
        var btn = el('sqlRunBtn');
        btn.disabled = true; btn.classList.add('loading');
        setSqlMeta('执行中...', false);
        fetch(BASE + '/sql', {
            method: 'POST',
            headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
            body: JSON.stringify({ sql: sql })
        }).then(function (r) {
            if (r.status === 401) { onTokenInvalid(); throw new Error('token 失效或缺失'); }
            return r.json().then(function (res) {
                if (!res || res.code !== 1) throw new Error((res && res.msg) || ('HTTP ' + r.status));
                return res.data;
            });
        }).then(function (data) {
            renderSqlResult(data);
            setSqlMeta('返回 ' + data.totalRows + ' 行' + (data.truncated ? '（超上限，仅前 1000）' : '') + ' · ' + (data.columns || []).length + ' 列', false);
        }).catch(function (e) {
            renderSqlError(e.message);
            setSqlMeta('失败: ' + e.message, true);
        }).then(function () {
            btn.disabled = false; btn.classList.remove('loading');
        });
    }
    function renderSqlResult(data) {
        var tbl = el('sqlResultTable');
        var cols = data.columns || [];
        tbl.querySelector('thead').innerHTML = '<tr>' + cols.map(function (c) { return '<th>' + esc(c) + '</th>'; }).join('') + '</tr>';
        var tb = tbl.querySelector('tbody');
        tb.innerHTML = '';
        (data.rows || []).forEach(function (row) {
            var tr = document.createElement('tr');
            tr.innerHTML = row.map(function (cell) { return '<td>' + esc(cell) + '</td>'; }).join('');
            tb.appendChild(tr);
        });
        if (!(data.rows && data.rows.length)) {
            tb.innerHTML = '<tr><td style="text-align:center;color:#8492a6" colspan="' + (cols.length || 1) + '">无数据</td></tr>';
        }
    }
    function renderSqlError(msg) {
        var tbl = el('sqlResultTable');
        tbl.querySelector('thead').innerHTML = '<tr><th>错误</th></tr>';
        tbl.querySelector('tbody').innerHTML = '<tr><td class="down">' + esc(msg) + '</td></tr>';
    }
    function setSqlMeta(text, isError) {
        var m = el('sqlMeta');
        m.textContent = text;
        m.className = 'sql-meta' + (isError ? ' err' : '');
    }

    function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
    function shortId(id) { return id && id.length > 14 ? id.slice(0, 12) + '…' : id; }
    function shortMsg(s) { s = String(s == null ? '' : s); return s.length > 40 ? s.slice(0, 38) + '…' : s; }

    // ---------- 穿透下钻：图表点击 -> 明细分页表 ----------
    /** 绑定图表 click（仅绑一次，setOption(notMerge) 刷新不影响已注册事件）。 */
    function bindClick(domId, handler) {
        var c = charts[domId];
        if (!c || c._drillBound) return;
        c.on('click', handler);
        c._drillBound = true;
    }
    function describeFilter(f) {
        if (!f) return '明细';
        if (f._bucket) return '耗时: ' + f._bucket;
        if (f.status) return '状态: ' + f.status;
        if (f.engineType) return '引擎: ' + f.engineType;
        return '明细';
    }
    /** 触发穿透：filter 合并当前时间窗，重置到第 1 页，滚动定位到明细分页表。 */
    function drillJobs(filter) {
        drillState.filter = filter || {};
        drillState.current = 1;
        el('drillPanel').style.display = '';
        el('drillTitle').textContent = describeFilter(filter);
        loadDrill();
        el('drillPanel').scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    function loadDrill() {
        el('drillHint').textContent = '加载中...';
        var q = queryParams() + '&current=' + drillState.current + '&size=' + drillState.size;
        for (var k in drillState.filter) {
            if (k.charAt(0) === '_') continue;
            var v = drillState.filter[k];
            if (v != null && v !== '') q += '&' + k + '=' + encodeURIComponent(v);
        }
        get('/jobs?' + q).then(function (page) {
            renderDrill(page);
            el('drillHint').textContent = '';
        }).catch(function (e) {
            el('drillHint').textContent = '加载失败: ' + e.message;
            el('drillTable').querySelector('tbody').innerHTML =
                '<tr><td colspan="7" style="text-align:center;color:#ee6666">' + esc(e.message) + '</td></tr>';
            el('drillPageInfo').textContent = '-';
        });
    }
    function renderDrill(page) {
        drillState.total = (page && page.total) ? Number(page.total) : 0;
        var tb = el('drillTable').querySelector('tbody');
        tb.innerHTML = '';
        (page && page.records ? page.records : []).forEach(function (r) {
            var tr = document.createElement('tr');
            tr.className = 'clickable';
            tr.innerHTML =
                '<td class="mono" title="' + esc(r.jobId || '') + '">' + esc(shortId(r.jobId)) + '</td>' +
                '<td>' + esc(r.userName || r.userId || '-') + '</td>' +
                '<td>' + esc(r.engineType || '-') + '</td>' +
                '<td><span class="st st-' + esc((r.status || 'unknown').toLowerCase()) + '">' + esc(r.status || '-') + '</span></td>' +
                '<td>' + fmtDate(r.submitTime) + '</td>' +
                '<td>' + fmtDur(r.durationMs) + '</td>' +
                '<td class="mono" title="' + esc(r.executorInstance || '') + '">' + esc(shortId(r.executorInstance) || '-') + '</td>';
            tr.onclick = (function (jobId) { return function () { if (jobId) openDrawer(jobId); }; })(r.jobId);
            tb.appendChild(tr);
        });
        if (!(page && page.records && page.records.length)) {
            tb.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#8492a6">无数据</td></tr>';
        }
        var cur = drillState.current, sz = drillState.size, total = drillState.total;
        var pages = sz > 0 ? Math.ceil(total / sz) : 0;
        el('drillPageInfo').textContent = total > 0 ? ('第 ' + cur + '/' + pages + ' 页 · 共 ' + total + ' 条') : '无数据';
        el('drillPrev').disabled = cur <= 1;
        el('drillNext').disabled = cur >= pages || pages === 0;
    }

    // ---------- 穿透下钻：明细行 -> Job 详情抽屉 ----------
    function openDrawer(jobId) {
        drawerState.jobId = jobId;
        drawerState.logOffset = 0;
        drawerState.logDone = false;
        if (drawerState.logTimer) { clearTimeout(drawerState.logTimer); drawerState.logTimer = null; }
        el('drawerTitle').textContent = 'Job ' + shortId(jobId);
        el('drawerLog').textContent = '';
        el('logMeta').textContent = '';
        el('logStatus').textContent = '';
        el('logMore').disabled = true;
        el('drawerJobMeta').innerHTML = '';
        el('drawerTaskTable').querySelector('tbody').innerHTML =
            '<tr><td colspan="7" style="text-align:center;color:#8492a6">加载中...</td></tr>';
        el('jobDrawer').classList.add('open');
        el('jobDrawer').setAttribute('aria-hidden', 'false');
        el('drawerMask').style.display = '';
        document.body.style.overflow = 'hidden';
        get('/job-detail?jobId=' + encodeURIComponent(jobId)).then(function (d) {
            renderDrawerMeta(d);
            loadDrawerLog();   // job 确认存在后再拉日志（不存在则不轮询，防死循环）
        }).catch(function (e) {
            el('drawerJobMeta').innerHTML = '<div class="down">Job 详情加载失败: ' + esc(e.message) + '</div>';
            el('drawerTaskTable').querySelector('tbody').innerHTML = '';
            el('logStatus').textContent = 'Job 不存在或已失效，无日志';
            drawerState.logDone = true;   // 阻止日志轮询
        });
    }
    /** 顶栏 Job ID 搜索：输入完整 jobId 回车/点查看 -> 复用 openDrawer 打开详情抽屉。 */
    function searchJob() {
        var v = (el('jobIdInput').value || '').trim();
        if (!v) { el('jobIdInput').focus(); return; }
        openDrawer(v);
    }
    function closeDrawer() {
        el('jobDrawer').classList.remove('open');
        el('jobDrawer').setAttribute('aria-hidden', 'true');
        el('drawerMask').style.display = 'none';
        document.body.style.overflow = '';
        if (drawerState.logTimer) { clearTimeout(drawerState.logTimer); drawerState.logTimer = null; }
    }
    function renderDrawerMeta(d) {
        var rows = [
            ['Job', d.jobId], ['状态', d.status], ['引擎', d.engineType],
            ['提交时间', fmtDate(d.submitTime)], ['SQL 段数', (d.tasks || []).length]
        ];
        el('drawerJobMeta').innerHTML = '<table class="meta-tbl">' + rows.map(function (r) {
            return '<tr><th>' + esc(r[0]) + '</th><td>' + esc(r[1] == null ? '-' : r[1]) + '</td></tr>';
        }).join('') + '</table>';
        renderDrawerTasks(d.tasks || []);
    }
    function renderDrawerTasks(tasks) {
        var tb = el('drawerTaskTable').querySelector('tbody');
        tb.innerHTML = '';
        tasks.forEach(function (t) {
            var tr = document.createElement('tr');
            tr.className = 'clickable';
            tr.innerHTML =
                '<td>' + (t.segmentIndex != null ? t.segmentIndex : '-') + '</td>' +
                '<td><span class="st st-' + esc((t.status || 'unknown').toLowerCase()) + '">' + esc(t.status || '-') + '</span></td>' +
                '<td>' + esc(t.sqlType || '-') + '</td>' +
                '<td>' + fmtDur(t.durationMs) + '</td>' +
                '<td>' + esc(t.failStage || '-') + '</td>' +
                '<td>' + fmtNum(t.resultRows) + '</td>' +
                '<td class="err-cell" title="' + esc(t.errorMessage || '') + '">' + esc(t.errorMessage ? shortMsg(t.errorMessage) : '-') + '</td>';
            tr.onclick = (function (task) { return function () { toggleTaskDetail(tr, task); }; })(t);
            tb.appendChild(tr);
        });
        if (!tasks.length) tb.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#8492a6">无 Task</td></tr>';
    }
    /** Task 行展开：显示 SQL + 按需拉 task-detail（扫描/时间线）/ task-log。 */
    function toggleTaskDetail(tr, t) {
        var next = tr.nextElementSibling;
        if (next && next.className === 'task-expand') { next.parentNode.removeChild(next); return; }
        var row = document.createElement('tr');
        row.className = 'task-expand';
        var cell = document.createElement('td');
        cell.colSpan = 7;
        cell.innerHTML =
            '<div class="task-expand-body"><b>SQL</b><pre class="code-block">' + esc(t.sqlContent || '(无)') + '</pre>' +
            (t.prefixSql ? '<b>Prefix</b><pre class="code-block">' + esc(t.prefixSql) + '</pre>' : '') +
            '<div class="task-actions">' +
            '<button type="button" class="link-btn" data-act="taskdetail">Task 详情(扫描/时间线)</button>' +
            '<button type="button" class="link-btn" data-act="tasklog">Task 日志</button></div>' +
            '<div class="task-extra"></div></div>';
        row.appendChild(cell);
        tr.parentNode.insertBefore(row, tr.nextSibling);
        var extra = cell.querySelector('.task-extra');
        cell.querySelector('[data-act="taskdetail"]').onclick = function () {
            extra.innerHTML = '<i>加载中...</i>';
            get('/task-detail?taskId=' + encodeURIComponent(t.taskId)).then(function (td) {
                extra.innerHTML = renderTaskExtra(td);
            }).catch(function (e) { extra.innerHTML = '<span class="down">失败: ' + esc(e.message) + '</span>'; });
        };
        cell.querySelector('[data-act="tasklog"]').onclick = function () {
            extra.innerHTML = '<i>加载中...</i>';
            get('/task-log?taskId=' + encodeURIComponent(t.taskId) + '&offset=0&limit=100').then(function (r) {
                extra.innerHTML = '<pre class="code-block">' + esc((r.lines || []).join('\n')) + '</pre>';
            }).catch(function (e) { extra.innerHTML = '<span class="down">失败: ' + esc(e.message) + '</span>'; });
        };
    }
    function renderTaskExtra(td) {
        var rows = [
            ['scanRows', td.scanRows], ['scanBytes', td.scanBytes], ['affectedRows', td.affectedRows],
            ['durationMs', td.durationMs], ['errorCode', td.errorCode], ['failReasonCategory', td.failReasonCategory],
            ['executorInstance', td.executorInstance], ['enqueueTime', fmtDate(td.enqueueTime)],
            ['startTime', fmtDate(td.startTime)], ['fetchStartTime', fmtDate(td.fetchStartTime)],
            ['writeStartTime', fmtDate(td.writeStartTime)], ['finishTime', fmtDate(td.finishTime)]
        ];
        return '<table class="meta-tbl">' + rows.map(function (r) {
            return '<tr><th>' + esc(r[0]) + '</th><td>' + esc(r[1] == null ? '-' : r[1]) + '</td></tr>';
        }).join('') + '</table>';
    }
    /** Job 日志增量轮询：复用 LogResponse 的 hasMore/complete 契约。
     *  hasMore=true -> 续翻页；hasMore=false 且 complete=false -> 2s 轮询续拉（执行中未 finalize）；complete=true -> 停。 */
    function loadDrawerLog() {
        if (drawerState.logDone) return;
        get('/job-log?jobId=' + encodeURIComponent(drawerState.jobId) +
            '&offset=' + drawerState.logOffset + '&limit=1000').then(function (r) {
            appendLog(r.lines || []);
            drawerState.logOffset += (r.lines || []).length;
            el('logMeta').textContent = drawerState.logOffset + ' 行';
            el('logMore').disabled = !r.hasMore;
            if (r.complete) {
                markLogDone();
                el('logStatus').textContent = '日志已完整';
                return;
            }
            if (r.hasMore) {
                el('logStatus').textContent = '分页未完，点"加载更多"或自动续拉';
                drawerState.logTimer = setTimeout(loadDrawerLog, 1500);
            } else {
                el('logStatus').textContent = '执行中…';
                drawerState.logTimer = setTimeout(loadDrawerLog, 2000);
            }
        }).catch(function (e) {
            el('logStatus').textContent = '日志加载失败: ' + e.message;
            drawerState.logTimer = setTimeout(loadDrawerLog, 5000);
        });
    }
    function appendLog(lines) {
        var pre = el('drawerLog');
        var wasNearBottom = pre.scrollTop + pre.clientHeight >= pre.scrollHeight - 40;
        if (pre.textContent && pre.textContent.length > 0) pre.textContent += '\n';
        pre.textContent += lines.join('\n');
        if (wasNearBottom) pre.scrollTop = pre.scrollHeight;
    }
    function markLogDone() {
        drawerState.logDone = true;
        if (drawerState.logTimer) { clearTimeout(drawerState.logTimer); drawerState.logTimer = null; }
        el('logMore').disabled = true;
    }

    // ---------- 加载 ----------
    function load() {
        var q = queryParams();
        setStatus('load', '加载中');
        setRefresh(true);
        Promise.all([
            get('/overview?' + q),
            get('/trends?' + q),
            get('/executors?onlineOnly=true'),
            get('/servers?onlineOnly=true'),
            get('/topn?' + q + '&limit=10'),
            get('/task-failures?' + q),
            get('/instance-load?' + q),
            get('/task-trends?' + q)
        ]).then(function (r) {
            renderOverview(r[0]);
            renderTrends(r[1]);
            renderTaskTrends(r[7]);
            renderExecutors(r[2]);
            renderServers(r[3]);
            renderTopN(r[4]);
            renderTaskFail(r[5]);
            renderInstanceLoad(r[6]);
            renderJvm(r[2], r[3]);
            setRefresh(false);
            setStatus('ok', '数据正常');
            setStatusText('更新于 ' + fmtTime(new Date()));
        }).catch(function (e) {
            console.error(e);
            setRefresh(false);
            setStatus('err', '加载失败: ' + e.message);
            setStatusText('更新失败 ' + fmtTime(new Date()));
        });
    }

    // ---------- 启动 ----------
    function startTimer() {
        stopTimer();
        if (el('autoRefresh').checked) timer = setInterval(load, REFRESH_MS);
    }
    function stopTimer() { if (timer) { clearInterval(timer); timer = null; } }

    // flatpickr 中文 + 时分选择（24h），离线自包含
    if (typeof flatpickr !== 'undefined') {
        try { flatpickr.localize(flatpickr.l10ns.zh); } catch (e) { /* l10n 缺失时退英文 */ }
        var fpOpts = {
            enableTime: true,
            dateFormat: 'Y-m-d H:i',
            time_24hr: true,
            minuteIncrement: 1,
            allowInput: false,
            onChange: loadDebounced
        };
        if (el('rangeStart')) fpStart = flatpickr('#rangeStart', fpOpts);
        if (el('rangeEnd')) fpEnd = flatpickr('#rangeEnd', fpOpts);
    }

    window.addEventListener('resize', function () { for (var k in charts) if (charts.hasOwnProperty(k)) charts[k].resize(); });
    el('windowSel').addEventListener('change', function () {
        syncRangeUI();
        // 顶部时间窗是主控：切回顶部窗口即恢复联动，JVM 按钮高亮同步到对应粒度
        jvmState.followTop = true;
        syncJvmBtnByTopWindow();
        load();
    });
    el('autoRefresh').addEventListener('change', startTimer);
    el('refreshBtn').addEventListener('click', function () {
        load();
        // 重置 30s 倒计时，避免刚手动刷新又立刻自动刷新
        if (el('autoRefresh').checked) startTimer();
    });
    // SQL 控制台：点击执行 + Ctrl/Cmd+Enter
    el('sqlRunBtn').addEventListener('click', runSql);
    el('sqlInput').addEventListener('keydown', function (e) {
        if ((e.ctrlKey || e.metaKey) && (e.key === 'Enter' || e.keyCode === 13)) {
            e.preventDefault();
            runSql();
        }
    });

    // 穿透明细面板：关闭 + 分页 + 每页条数
    el('drillClose').addEventListener('click', function () { el('drillPanel').style.display = 'none'; });

    // JVM 监控：角色开关 + 全选/全不选（勾选/角色变化 -> 重拉时序）
    el('jvmRoleServer').addEventListener('change', function (e) {
        jvmState.roleServer = e.target.checked;
        syncJvmChips(); fetchJvmSeries();
    });
    el('jvmRoleExecutor').addEventListener('change', function (e) {
        jvmState.roleExecutor = e.target.checked;
        syncJvmChips(); fetchJvmSeries();
    });
    el('jvmSelectAll').addEventListener('click', function () {
        jvmCandidates().forEach(function (c) { if (roleOn(c.role)) jvmState.selected[c.key] = true; });
        syncJvmChips(); fetchJvmSeries();
    });
    el('jvmSelectNone').addEventListener('click', function () {
        jvmCandidates().forEach(function (c) { if (roleOn(c.role)) jvmState.selected[c.key] = false; });
        syncJvmChips(); fetchJvmSeries();
    });
    // JVM 时间窗：1h/2h/6h/12h/24h/3d/7d 切换 -> 局部覆盖（脱离顶部联动），仅JVM用该分钟窗重拉
    document.querySelectorAll('.jvm-window-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            document.querySelectorAll('.jvm-window-btn').forEach(function (b) { b.classList.remove('active'); });
            btn.classList.add('active');
            jvmState.windowMin = parseInt(btn.dataset.min, 10) || 30;
            jvmState.followTop = false;  // 用户主动点JVM按钮=局部覆盖，不再跟随顶部
            fetchJvmSeries();
        });
    });

    // 顶栏 Job ID 搜索：回车 + 查看按钮
    el('jobIdSearchBtn').addEventListener('click', searchJob);
    el('jobIdInput').addEventListener('keydown', function (e) {
        if (e.key === 'Enter') { e.preventDefault(); searchJob(); }
    });
    el('drillPrev').addEventListener('click', function () {
        if (drillState.current > 1) { drillState.current--; loadDrill(); }
    });
    el('drillNext').addEventListener('click', function () {
        drillState.current++; loadDrill();
    });
    el('drillSize').addEventListener('change', function () {
        drillState.size = parseInt(this.value, 10) || 20;
        drillState.current = 1;
        loadDrill();
    });
    // Job 详情抽屉：关闭 + mask 点击 + 日志加载更多 + Esc 关闭
    el('drawerClose').addEventListener('click', closeDrawer);
    el('drawerMask').addEventListener('click', closeDrawer);
    el('logMore').addEventListener('click', loadDrawerLog);
    window.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && el('jobDrawer').classList.contains('open')) closeDrawer();
    });
    // 启动：JVM 按钮高亮对齐顶部默认窗口，然后首屏加载
    // 访问令牌：先取一次（URL ?token= -> 写 localStorage 并清 URL），无 token 也尝试加载——
    //   后端未启用鉴权（token 配置空）时请求 200 正常工作；后端启用鉴权时首请求 401 触发遮罩。
    getToken();
    syncJvmBtnByTopWindow();
    load();
    startTimer();
})();
