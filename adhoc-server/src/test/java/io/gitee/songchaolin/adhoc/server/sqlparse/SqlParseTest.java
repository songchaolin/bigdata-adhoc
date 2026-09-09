package io.gitee.songchaolin.adhoc.server.sqlparse;

import io.gitee.songchaolin.adhoc.common.enums.EngineType;
import io.gitee.songchaolin.adhoc.sqlparser.model.ProcessedSqlSegment;
import io.gitee.songchaolin.adhoc.sqlparser.preprocess.SqlScriptProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
public class SqlParseTest {

    @Test
    public void test1() {
        String sql = "\n" +
                "WITH parsed AS (\n" +
                "    SELECT dfp\n" +
                "         , out_code\n" +
                "         , platform\n" +
                "         , cust_id\n" +
                "         , create_time\n" +
                "         , detail\n" +
                "         , FROM_JSON(\n" +
                "            detail,\n" +
                "            'STRUCT<\n" +
                "                adBlock: STRING,\n" +
                "                addBehavior: STRING,\n" +
                "                algID: STRING,\n" +
                "                androidID: STRING,\n" +
                "                appInstallPosition: STRING,\n" +
                "                appMemory: STRING,\n" +
                "                appUpdateTime: STRING,\n" +
                "                appVersion: STRING,\n" +
                "                audio: STRING,\n" +
                "                availableMemory: STRING,\n" +
                "                availableScreenResolution: STRING,\n" +
                "                availableSD: STRING,\n" +
                "                availableSystem: STRING,\n" +
                "                baseStation: STRING,\n" +
                "                batteryHealth: STRING,\n" +
                "                batteryLevel: STRING,\n" +
                "                batteryStatus: STRING,\n" +
                "                batteryTemperature: STRING,\n" +
                "                bluetooth: STRING,\n" +
                "                bluetoothEnabled: STRING,\n" +
                "                board: STRING,\n" +
                "                bootLoader: STRING,\n" +
                "                brand: STRING,\n" +
                "                brightness: STRING,\n" +
                "                browserEngine: STRING,\n" +
                "                browserEngineVersion: STRING,\n" +
                "                browserName: STRING,\n" +
                "                browserVersion: STRING,\n" +
                "                canvasHash: STRING,\n" +
                "                carrier: STRING,\n" +
                "                ccAppID: STRING,\n" +
                "                CCID: STRING,\n" +
                "                CDID: STRING,\n" +
                "                celluarEnable: STRING,\n" +
                "                celluarPermission: STRING,\n" +
                "                clientIP: STRING,\n" +
                "                cloakTags: STRING,\n" +
                "                colorDepth: STRING,\n" +
                "                cookieCode: STRING,\n" +
                "                cookieEnabled: STRING,\n" +
                "                cookieSource: STRING,\n" +
                "                coordinates: STRING,\n" +
                "                cpuABI: STRING,\n" +
                "                cpuClass: STRING,\n" +
                "                cpuName: STRING,\n" +
                "                cpuNum: STRING,\n" +
                "                cpuType: STRING,\n" +
                "                crossCode: STRING,\n" +
                "                CSID: STRING,\n" +
                "                currentWiFi: STRING,\n" +
                "                debugTags: STRING,\n" +
                "                decCCID: STRING,\n" +
                "                deepSleepTime: STRING,\n" +
                "                deviceMemory: STRING,\n" +
                "                deviceName: STRING,\n" +
                "                devicePixelRatio: STRING,\n" +
                "                deviceType: STRING,\n" +
                "                deviceVendor: STRING,\n" +
                "                dfpChangeType: STRING,\n" +
                "                dfpType: STRING,\n" +
                "                dfpEvent: STRING,\n" +
                "                dfpUUID: STRING,\n" +
                "                display: STRING,\n" +
                "                dns: STRING,\n" +
                "                doNotTrack: STRING,\n" +
                "                dynamicLib: STRING,\n" +
                "                enableAdb: STRING,\n" +
                "                fingerprint: STRING,\n" +
                "                firstInstallTime: STRING,\n" +
                "                flashVersion: STRING,\n" +
                "                fontsHash: STRING,\n" +
                "                fridaTags: STRING,\n" +
                "                gpsEnable: STRING,\n" +
                "                hardware: STRING,\n" +
                "                hardwareConcurrency: STRING,\n" +
                "                hashCode: STRING,\n" +
                "                hasLiedBrowser: STRING,\n" +
                "                hasLiedLanguages: STRING,\n" +
                "                hasLiedOs: STRING,\n" +
                "                hasLiedResolution: STRING,\n" +
                "                hasSimCard: STRING,\n" +
                "                hookedFuncs: STRING,\n" +
                "                host: STRING,\n" +
                "                iCloudAvailable: STRING,\n" +
                "                IDFA: STRING,\n" +
                "                IDFV: STRING,\n" +
                "                IMEI: STRING,\n" +
                "                IMEIS: STRING,\n" +
                "                IMSI: STRING,\n" +
                "                indexedDb: STRING,\n" +
                "                injectedLibs: STRING,\n" +
                "                isBackground: STRING,\n" +
                "                isCycript: STRING,\n" +
                "                isCydia: STRING,\n" +
                "                isDebug: STRING,\n" +
                "                isDebugPackage: STRING,\n" +
                "                isDeviceTimeException: STRING,\n" +
                "                isFrida: STRING,\n" +
                "                isHarmony: STRING,\n" +
                "                isHooked: STRING,\n" +
                "                isInAudio: STRING,\n" +
                "                isInCall: STRING,\n" +
                "                isIncognito: STRING,\n" +
                "                isInjected: STRING,\n" +
                "                isMockLocation: STRING,\n" +
                "                isModifyHosts: STRING,\n" +
                "                isMulti: STRING,\n" +
                "                isOnMac: STRING,\n" +
                "                isProxy: STRING,\n" +
                "                isRiskBrowser: STRING,\n" +
                "                isRoaming: STRING,\n" +
                "                isRooted: STRING,\n" +
                "                isSandboxBroken: STRING,\n" +
                "                isScreenProjection: STRING,\n" +
                "                isScreenRecording: STRING,\n" +
                "                isScreenShared: STRING,\n" +
                "                isSelenium: STRING,\n" +
                "                isStartupTimeShort: STRING,\n" +
                "                isSubstrate: STRING,\n" +
                "                isVM: STRING,\n" +
                "                isVPN: STRING,\n" +
                "                isWiredHeadsetOn: STRING,\n" +
                "                isXposed: STRING,\n" +
                "                javaEnabled: STRING,\n" +
                "                language: STRING,\n" +
                "                localCode: STRING,\n" +
                "                localStorage: STRING,\n" +
                "                locationEnabled: STRING,\n" +
                "                manufacturer: STRING,\n" +
                "                match: STRING,\n" +
                "                matchType: STRING,\n" +
                "                mimeTypesHash: STRING,\n" +
                "                model: STRING,\n" +
                "                multiTags: STRING,\n" +
                "                navigatorPlatform: STRING,\n" +
                "                nearbyBaseStation: STRING,\n" +
                "                networkCountry: STRING,\n" +
                "                networkOperator: STRING,\n" +
                "                networkType: STRING,\n" +
                "                node: STRING,\n" +
                "                OAID: STRING,\n" +
                "                openDatabase: STRING,\n" +
                "                os: STRING,\n" +
                "                osVersion: STRING,\n" +
                "                packageName: STRING,\n" +
                "                parseIP: STRING,\n" +
                "                passiveCode: STRING,\n" +
                "                passiveIP: STRING,\n" +
                "                passiveIPDetail: STRING,\n" +
                "                permission: STRING,\n" +
                "                phoneType: STRING,\n" +
                "                pluginsHash: STRING,\n" +
                "                possibility: STRING,\n" +
                "                product: STRING,\n" +
                "                resolution: STRING,\n" +
                "                rootedTags: STRING,\n" +
                "                rootTags: STRING,\n" +
                "                screenHeight: STRING,\n" +
                "                screenOffTime: STRING,\n" +
                "                screenResolution: STRING,\n" +
                "                screenWidth: STRING,\n" +
                "                sdkVersion: STRING,\n" +
                "                sensorListHash: STRING,\n" +
                "                sessionStorage: STRING,\n" +
                "                signId: STRING,\n" +
                "                simCount: STRING,\n" +
                "                simCountry: STRING,\n" +
                "                startupTime: STRING,\n" +
                "                statusBarHeight: STRING,\n" +
                "                systemID: STRING,\n" +
                "                systemPlatform: STRING,\n" +
                "                systemVersion: STRING,\n" +
                "                tags: STRING,\n" +
                "                timestamp: STRING,\n" +
                "                timezone: STRING,\n" +
                "                timezoneOffset: STRING,\n" +
                "                totalMemory: STRING,\n" +
                "                totalSD: STRING,\n" +
                "                totalSystem: STRING,\n" +
                "                touchSupport: STRING,\n" +
                "                userAgent: STRING,\n" +
                "                userInfo: STRING,\n" +
                "                version: STRING,\n" +
                "                vmName: STRING,\n" +
                "                vmTags: STRING,\n" +
                "                wapSmartID: STRING,\n" +
                "                webdriver: STRING,\n" +
                "                webglHash: STRING,\n" +
                "                webglVendorAndRenderer: STRING,\n" +
                "                webSmartID: STRING,\n" +
                "                wifiEnable: STRING,\n" +
                "                wifiListHash: STRING,\n" +
                "                wifiMacAddress: STRING,\n" +
                "                windowHeight: STRING,\n" +
                "                windowWidth: STRING,\n" +
                "                wxSmartID: STRING,\n" +
                "                xposedTags: STRING,\n" +
                "                isCamHiJ: STRING,\n" +
                "                camHiJ: STRING,\n" +
                "                ODID: STRING,\n" +
                "                webgl: STRING,\n" +
                "                sharedTags: STRING,\n" +
                "                recordTags: STRING,\n" +
                "                isDevOpen: STRING,\n" +
                "                isAccOpen: STRING,\n" +
                "                isVirCam: STRING,\n" +
                "                virCamTags: STRING,\n" +
                "                isSpeakerActive: STRING,\n" +
                "                bundleType: STRING,\n" +
                "                celluarIP: STRING,\n" +
                "                certTeamID: STRING,\n" +
                "                dySmartID: STRING,\n" +
                "                memorySize: STRING,\n" +
                "                uid: STRING,\n" +
                "                installPkg: STRING,\n" +
                "                isOnWin: STRING,\n" +
                "                isRemoteControl: STRING,\n" +
                "                accOpenPkg: STRING,\n" +
                "                isVirVoice: STRING,\n" +
                "                audioMode: STRING,\n" +
                "                aodSwitch: STRING,\n" +
                "                isCloudPhone: STRING,\n" +
                "                fraudRisk: STRING,\n" +
                "                clickRisk: STRING,\n" +
                "                isTrollstore: STRING,\n" +
                "                isTrollstoreApp: STRING,\n" +
                "                trollStoreTags: STRING,\n" +
                "                isSSLBypass: STRING,\n" +
                "                sslBypassTags: STRING,\n" +
                "                isAIControl: STRING,\n" +
                "                propCheck: STRING,\n" +
                "                isAutoControl: STRING,\n" +
                "                autoTags: STRING,\n" +
                "                hasFloatView: STRING,\n" +
                "                isUnLock: STRING,\n" +
                "                isRom: STRING,\n" +
                "                isTampered: STRING,\n" +
                "                isRepackage: STRING,\n" +
                "                isSpider: STRING,\n" +
                "                isDNSHij: STRING,\n" +
                "                isFrequentEnv: STRING\n" +
                "            >'\n" +
                "        ) AS json_struct\n" +
                "    FROM jms_ods.ods_bsdfp_record_dt\n" +
                "    WHERE dt = '{{ execution_date |  cst_ds }}'\n" +
                ")\n" +
                "\n" +
                "insert overwrite table jms_dwd.dwd_rc_bsdfp_record_dt partition(dt)\n" +
                "SELECT dfp\n" +
                "     , out_code\n" +
                "     , platform\n" +
                "     , cust_id\n" +
                "     , create_time\n" +
                "     -- JSON内的字段从 json_struct 中提取\n" +
                "     , json_struct.adBlock                AS ad_block\n" +
                "     , json_struct.addBehavior            AS add_behavior\n" +
                "     , json_struct.algID                  AS alg_id\n" +
                "     , json_struct.androidID              AS android_id\n" +
                "     , json_struct.appInstallPosition     AS app_install_position\n" +
                "     , json_struct.appMemory              AS app_memory\n" +
                "     , json_struct.appUpdateTime          AS app_update_time\n" +
                "     , json_struct.appVersion             AS app_version\n" +
                "     , json_struct.audio                  AS audio\n" +
                "     , json_struct.availableMemory        AS available_memory\n" +
                "     , json_struct.availableScreenResolution AS available_screen_resolution\n" +
                "     , json_struct.availableSD            AS available_sd\n" +
                "     , json_struct.availableSystem        AS available_system\n" +
                "     , json_struct.baseStation            AS base_station\n" +
                "     , json_struct.batteryHealth          AS battery_health\n" +
                "     , json_struct.batteryLevel           AS battery_level\n" +
                "     , json_struct.batteryStatus          AS battery_status\n" +
                "     , json_struct.batteryTemperature     AS battery_temperature\n" +
                "     , json_struct.bluetooth              AS bluetooth\n" +
                "     , json_struct.bluetoothEnabled       AS bluetooth_enabled\n" +
                "     , json_struct.board                  AS board\n" +
                "     , json_struct.bootLoader             AS boot_loader\n" +
                "     , json_struct.brand                  AS brand\n" +
                "     , json_struct.brightness             AS brightness\n" +
                "     , json_struct.browserEngine          AS browser_engine\n" +
                "     , json_struct.browserEngineVersion   AS browser_engine_version\n" +
                "     , json_struct.browserName            AS browser_name\n" +
                "     , json_struct.browserVersion         AS browser_version\n" +
                "     , json_struct.canvasHash             AS canvas_hash\n" +
                "     , json_struct.carrier                AS carrier\n" +
                "     , json_struct.ccAppID                AS cc_app_id\n" +
                "     , json_struct.CCID                   AS ccid\n" +
                "     , json_struct.CDID                   AS cdid\n" +
                "     , json_struct.celluarEnable          AS celluar_enable\n" +
                "     , json_struct.celluarPermission      AS celluar_permission\n" +
                "     , json_struct.clientIP               AS client_ip\n" +
                "     , json_struct.cloakTags              AS cloak_tags\n" +
                "     , json_struct.colorDepth             AS color_depth\n" +
                "     , json_struct.cookieCode             AS cookie_code\n" +
                "     , json_struct.cookieEnabled          AS cookie_enabled\n" +
                "     , json_struct.cookieSource           AS cookie_source\n" +
                "     , json_struct.coordinates            AS coordinates\n" +
                "     , json_struct.cpuABI                 AS cpu_abi\n" +
                "     , json_struct.cpuClass               AS cpu_class\n" +
                "     , json_struct.cpuName                AS cpu_name\n" +
                "     , json_struct.cpuNum                 AS cpu_num\n" +
                "     , json_struct.cpuType                AS cpu_type\n" +
                "     , json_struct.crossCode              AS cross_code\n" +
                "     , json_struct.CSID                   AS csid\n" +
                "     , json_struct.currentWiFi            AS current_wifi\n" +
                "     , json_struct.debugTags              AS debug_tags\n" +
                "     , json_struct.decCCID                AS dec_ccid\n" +
                "     , json_struct.deepSleepTime          AS deep_sleep_time\n" +
                "     , json_struct.deviceMemory           AS device_memory\n" +
                "     , json_struct.deviceName             AS device_name\n" +
                "     , json_struct.devicePixelRatio       AS device_pixel_ratio\n" +
                "     , json_struct.deviceType             AS device_type\n" +
                "     , json_struct.deviceVendor           AS device_vendor\n" +
                "     , json_struct.dfpChangeType          AS dfp_change_type\n" +
                "     , json_struct.dfpType                AS dfp_type\n" +
                "     , json_struct.dfpEvent               AS dfp_event\n" +
                "     , json_struct.dfpUUID                AS dfp_uuid\n" +
                "     , json_struct.display                AS display\n" +
                "     , json_struct.dns                    AS dns\n" +
                "     , json_struct.doNotTrack             AS do_not_track\n" +
                "     , json_struct.dynamicLib             AS dynamic_lib\n" +
                "     , json_struct.enableAdb              AS enable_adb\n" +
                "     , json_struct.fingerprint            AS fingerprint\n" +
                "     , json_struct.firstInstallTime       AS first_install_time\n" +
                "     , json_struct.flashVersion           AS flash_version\n" +
                "     , json_struct.fontsHash              AS fonts_hash\n" +
                "     , json_struct.fridaTags              AS frida_tags\n" +
                "     , json_struct.gpsEnable              AS gps_enable\n" +
                "     , json_struct.hardware               AS hardware\n" +
                "     , json_struct.hardwareConcurrency    AS hardware_concurrency\n" +
                "     , json_struct.hashCode               AS hash_code\n" +
                "     , json_struct.hasLiedBrowser         AS has_lied_browser\n" +
                "     , json_struct.hasLiedLanguages       AS has_lied_languages\n" +
                "     , json_struct.hasLiedOs              AS has_lied_os\n" +
                "     , json_struct.hasLiedResolution      AS has_lied_resolution\n" +
                "     , json_struct.hasSimCard             AS has_sim_card\n" +
                "     , json_struct.hookedFuncs            AS hooked_funcs\n" +
                "     , json_struct.host                   AS host\n" +
                "     , json_struct.iCloudAvailable        AS icloud_available\n" +
                "     , json_struct.IDFA                   AS idfa\n" +
                "     , json_struct.IDFV                   AS idfv\n" +
                "     , json_struct.IMEI                   AS imei\n" +
                "     , json_struct.IMEIS                  AS imeis\n" +
                "     , json_struct.IMSI                   AS imsi\n" +
                "     , json_struct.indexedDb              AS indexed_db\n" +
                "     , json_struct.injectedLibs           AS injected_libs\n" +
                "     , json_struct.isBackground           AS is_background\n" +
                "     , json_struct.isCycript              AS is_cycript\n" +
                "     , json_struct.isCydia                AS is_cydia\n" +
                "     , json_struct.isDebug                AS is_debug\n" +
                "     , json_struct.isDebugPackage         AS is_debug_package\n" +
                "     , json_struct.isDeviceTimeException  AS is_device_time_exception\n" +
                "     , json_struct.isFrida                AS is_frida\n" +
                "     , json_struct.isHarmony              AS is_harmony\n" +
                "     , json_struct.isHooked               AS is_hooked\n" +
                "     , json_struct.isInAudio              AS is_in_audio\n" +
                "     , json_struct.isInCall               AS is_in_call\n" +
                "     , json_struct.isIncognito            AS is_incognito\n" +
                "     , json_struct.isInjected             AS is_injected\n" +
                "     , json_struct.isMockLocation         AS is_mock_location\n" +
                "     , json_struct.isModifyHosts          AS is_modify_hosts\n" +
                "     , json_struct.isMulti                AS is_multi\n" +
                "     , json_struct.isOnMac                AS is_on_mac\n" +
                "     , json_struct.isProxy                AS is_proxy\n" +
                "     , json_struct.isRiskBrowser          AS is_risk_browser\n" +
                "     , json_struct.isRoaming              AS is_roaming\n" +
                "     , json_struct.isRooted               AS is_rooted\n" +
                "     , json_struct.isSandboxBroken        AS is_sandbox_broken\n" +
                "     , json_struct.isScreenProjection     AS is_screen_projection\n" +
                "     , json_struct.isScreenRecording      AS is_screen_recording\n" +
                "     , json_struct.isScreenShared         AS is_screen_shared\n" +
                "     , json_struct.isSelenium             AS is_selenium\n" +
                "     , json_struct.isStartupTimeShort     AS is_startup_time_short\n" +
                "     , json_struct.isSubstrate            AS is_substrate\n" +
                "     , json_struct.isVM                   AS is_vm\n" +
                "     , json_struct.isVPN                  AS is_vpn\n" +
                "     , json_struct.isWiredHeadsetOn       AS is_wired_headset_on\n" +
                "     , json_struct.isXposed               AS is_xposed\n" +
                "     , json_struct.javaEnabled            AS java_enabled\n" +
                "     , json_struct.language               AS language\n" +
                "     , json_struct.localCode              AS local_code\n" +
                "     , json_struct.localStorage           AS local_storage\n" +
                "     , json_struct.locationEnabled        AS location_enabled\n" +
                "     , json_struct.manufacturer           AS manufacturer\n" +
                "     , json_struct.match                  AS match\n" +
                "     , json_struct.matchType              AS match_type\n" +
                "     , json_struct.mimeTypesHash          AS mime_types_hash\n" +
                "     , json_struct.model                  AS model\n" +
                "     , json_struct.multiTags              AS multi_tags\n" +
                "     , json_struct.navigatorPlatform      AS navigator_platform\n" +
                "     , json_struct.nearbyBaseStation      AS nearby_base_station\n" +
                "     , json_struct.networkCountry         AS network_country\n" +
                "     , json_struct.networkOperator        AS network_operator\n" +
                "     , json_struct.networkType            AS network_type\n" +
                "     , json_struct.node                   AS node\n" +
                "     , json_struct.OAID                   AS oaid\n" +
                "     , json_struct.openDatabase           AS open_database\n" +
                "     , json_struct.os                     AS os\n" +
                "     , json_struct.osVersion              AS os_version\n" +
                "     , json_struct.packageName            AS package_name\n" +
                "     , json_struct.parseIP                AS parse_ip\n" +
                "     , json_struct.passiveCode            AS passive_code\n" +
                "     , json_struct.passiveIP              AS passive_ip\n" +
                "     , json_struct.passiveIPDetail        AS passive_ip_detail\n" +
                "     , json_struct.permission             AS permission\n" +
                "     , json_struct.phoneType              AS phone_type\n" +
                "     , json_struct.pluginsHash            AS plugins_hash\n" +
                "     , json_struct.possibility            AS possibility\n" +
                "     , json_struct.product                AS product\n" +
                "     , json_struct.resolution             AS resolution\n" +
                "     , json_struct.rootedTags             AS rooted_tags\n" +
                "     , json_struct.rootTags               AS root_tags\n" +
                "     , json_struct.screenHeight           AS screen_height\n" +
                "     , json_struct.screenOffTime          AS screen_off_time\n" +
                "     , json_struct.screenResolution       AS screen_resolution\n" +
                "     , json_struct.screenWidth            AS screen_width\n" +
                "     , json_struct.sdkVersion             AS sdk_version\n" +
                "     , json_struct.sensorListHash         AS sensor_list_hash\n" +
                "     , json_struct.sessionStorage         AS session_storage\n" +
                "     , json_struct.signId                 AS sign_id\n" +
                "     , json_struct.simCount               AS sim_count\n" +
                "     , json_struct.simCountry             AS sim_country\n" +
                "     , json_struct.startupTime            AS startup_time\n" +
                "     , json_struct.statusBarHeight        AS status_bar_height\n" +
                "     , json_struct.systemID               AS system_id\n" +
                "     , json_struct.systemPlatform         AS system_platform\n" +
                "     , json_struct.systemVersion          AS system_version\n" +
                "     , json_struct.tags                   AS tags\n" +
                "     , json_struct.timestamp              AS timestamp\n" +
                "     , json_struct.timezone               AS timezone\n" +
                "     , json_struct.timezoneOffset         AS timezone_offset\n" +
                "     , json_struct.totalMemory            AS total_memory\n" +
                "     , json_struct.totalSD                AS total_sd\n" +
                "     , json_struct.totalSystem            AS total_system\n" +
                "     , json_struct.touchSupport           AS touch_support\n" +
                "     , json_struct.userAgent              AS user_agent\n" +
                "     , json_struct.userInfo               AS user_info\n" +
                "     , json_struct.version                AS version\n" +
                "     , json_struct.vmName                 AS vm_name\n" +
                "     , json_struct.vmTags                 AS vm_tags\n" +
                "     , json_struct.wapSmartID             AS wap_smart_id\n" +
                "     , json_struct.webdriver              AS webdriver\n" +
                "     , json_struct.webglHash              AS webgl_hash\n" +
                "     , json_struct.webglVendorAndRenderer AS webgl_vendor_and_renderer\n" +
                "     , json_struct.webSmartID             AS web_smart_id\n" +
                "     , json_struct.wifiEnable             AS wifi_enable\n" +
                "     , json_struct.wifiListHash           AS wifi_list_hash\n" +
                "     , json_struct.wifiMacAddress         AS wifi_mac_address\n" +
                "     , json_struct.windowHeight           AS window_height\n" +
                "     , json_struct.windowWidth            AS window_width\n" +
                "     , json_struct.wxSmartID              AS wx_smart_id\n" +
                "     , json_struct.xposedTags             AS xposed_tags\n" +
                "     , json_struct.isCamHiJ               AS is_cam_hij\n" +
                "     , json_struct.camHiJ                 AS cam_hij\n" +
                "     , json_struct.ODID                   AS odid\n" +
                "     , json_struct.webgl                  AS webgl\n" +
                "     , json_struct.sharedTags             AS shared_tags\n" +
                "     , json_struct.recordTags             AS record_tags\n" +
                "     , json_struct.isDevOpen              AS is_dev_open\n" +
                "     , json_struct.isAccOpen              AS is_acc_open\n" +
                "     , json_struct.isVirCam               AS is_vir_cam\n" +
                "     , json_struct.virCamTags             AS vir_cam_tags\n" +
                "     , json_struct.isSpeakerActive        AS is_speaker_active\n" +
                "     , json_struct.bundleType             AS bundle_type\n" +
                "     , json_struct.celluarIP              AS celluar_ip\n" +
                "     , json_struct.certTeamID             AS cert_team_id\n" +
                "     , json_struct.dySmartID              AS dy_smart_id\n" +
                "     , json_struct.memorySize             AS memory_size\n" +
                "     , json_struct.uid                    AS uid\n" +
                "     , json_struct.installPkg             AS install_pkg\n" +
                "     , json_struct.isOnWin                AS is_on_win\n" +
                "     , json_struct.isRemoteControl        AS is_remote_control\n" +
                "     , json_struct.accOpenPkg             AS acc_open_pkg\n" +
                "     , json_struct.isVirVoice             AS is_vir_voice\n" +
                "     , json_struct.audioMode              AS audio_mode\n" +
                "     , json_struct.aodSwitch              AS aod_switch\n" +
                "     , json_struct.isCloudPhone           AS is_cloud_phone\n" +
                "     , json_struct.fraudRisk              AS fraud_risk\n" +
                "     , json_struct.clickRisk              AS click_risk\n" +
                "     , json_struct.isTrollstore           AS is_trollstore\n" +
                "     , json_struct.isTrollstoreApp        AS is_trollstore_app\n" +
                "     , json_struct.trollStoreTags         AS troll_store_tags\n" +
                "     , json_struct.isSSLBypass            AS is_ssl_bypass\n" +
                "     , json_struct.sslBypassTags          AS ssl_bypass_tags\n" +
                "     , json_struct.isAIControl            AS is_ai_control\n" +
                "     , json_struct.propCheck              AS prop_check\n" +
                "     , json_struct.isAutoControl          AS is_auto_control\n" +
                "     , json_struct.autoTags               AS auto_tags\n" +
                "     , json_struct.hasFloatView           AS has_float_view\n" +
                "     , json_struct.isUnLock               AS is_un_lock\n" +
                "     , json_struct.isRom                  AS is_rom\n" +
                "     , json_struct.isTampered             AS is_tampered\n" +
                "     , json_struct.isRepackage            AS is_repackage\n" +
                "     , json_struct.isSpider               AS is_spider\n" +
                "     , json_struct.isDNSHij               AS is_dns_hij\n" +
                "     , json_struct.isFrequentEnv          AS is_frequent_env\n" +
                "     , to_date(create_time) as dt\n" +
                "FROM parsed\n" +
                "distribute by dt, pmod(hash(dfp),5)\n" +
                ";\n";
        sql = "set spark.sql.storeAssignmentPolicy=LEGACY;\n" +
                "insert overwrite table jms_dwm.dwm_qt_task_detail_hi partition(dt)\n" +
                "select qt.id                                                   as id,\n" +
                "       qt.task_biz_id                                          as task_id,\n" +
                "       case qt.task_org_type\n" +
                "           when 1 then '系统'\n" +
                "           when 2 then '总部'\n" +
                "           when 3 then '网点'\n" +
                "           when 4 then '代理区'\n" +
                "           when 5 then 'BPO'\n" +
                "           end                                                 as task_org_type,\n" +
                "       case qt.task_status\n" +
                "           when 1 then 'CP等待中'\n" +
                "           when 2 then 'CP运行中'\n" +
                "           when 3 then 'CP失败的'\n" +
                "           when 4 then '复检待分配'\n" +
                "           when 5 then '待复检'\n" +
                "           when 6 then '已复检'\n" +
                "           when 7 then '申诉待分配'\n" +
                "           when 8 then '申诉待复检'\n" +
                "           when 9 then '申诉已复检'\n" +
                "           when 10 then '已完结'\n" +
                "           end                                                 as task_status,\n" +
                "       qt.task_schedule_time                                   as task_schedule_time,\n" +
                "       qt.task_complete_time                                   as task_complete_time,\n" +
                "       qt.task_format_date                                     as task_format_date,\n" +
                "       qt.task_retries_count                                   as task_retries_count,\n" +
                "       qt.qt_appeal_count                                      as qt_appeal_count,\n" +
                "       case qt.qt_last_rc_result\n" +
                "           when 1 then '合格'\n" +
                "           when 2 then '不合格'\n" +
                "           end                                                 as qt_last_rc_result,\n" +
                "       qt.qt_close_time                                        as qt_close_time,\n" +
                "       qt.session_id                                           as session_id,\n" +
                "       qt.session_network_code                                 as session_network_code,\n" +
                "       qt.session_network_name                                 as session_network_name,\n" +
                "       qt.session_agency_code                                  as session_agency_code,\n" +
                "       qt.session_agency_name                                  as session_agency_name,\n" +
                "       qt.session_start_time                                   as session_start_time,\n" +
                "       qt.session_end_time                                     as session_end_time,\n" +
                "       qt.session_time                                         as session_time,\n" +
                "       qt.session_round                                        as session_round,\n" +
                "       qt.session_channel_code                                 as session_channel_code,\n" +
                "       qt.session_channel_name                                 as session_channel_name,\n" +
                "       qt.session_eval_level                                   as session_eval_level,\n" +
                "       qt.session_eval_level_format                            as session_eval_level_format,\n" +
                "       qt.session_eval_remark                                  as session_eval_remark,\n" +
                "       qt.session_end_type                                     as session_end_type,\n" +
                "       qt.session_end_role                                     as session_end_role,\n" +
                "       qt.session_customer_id                                  as session_customer_id,\n" +
                "       qt.session_customer_name                                as session_customer_name,\n" +
                "       CASE \n" +
                "    -- 1. 空值或 '--' 直接返回\n" +
                "    WHEN cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string) IS NULL OR cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string) = '' OR cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string) = '--' THEN cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string)\n" +
                "    \n" +
                "    -- 2. 已脱敏（包含*）或长度<=4的直接返回\n" +
                "    WHEN cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string) LIKE '%*%' OR LENGTH(cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string)) <= 4 THEN cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string)\n" +
                "    \n" +
                "    -- 3. 包含'-'说明有分机号\n" +
                "    WHEN cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string) LIKE '%-%' THEN \n" +
                "      CONCAT(\n" +
                "        SUBSTR(REPLACE(SPLIT(cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string), '-')[0], ' ', ''), 1, 3),\n" +
                "        '****',\n" +
                "        SUBSTR(REPLACE(SPLIT(cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string), '-')[0], ' ', ''), -4),\n" +
                "        '-',\n" +
                "        SPLIT(cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string), '-')[1]\n" +
                "      )\n" +
                "    \n" +
                "    -- 4. 纯数字手机号\n" +
                "    WHEN cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string) REGEXP '^[0-9]+$' THEN \n" +
                "      CONCAT(\n" +
                "        SUBSTR(cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string), 1, 3),\n" +
                "        '****',\n" +
                "        SUBSTR(cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string), -4)\n" +
                "      )\n" +
                "    \n" +
                "    -- 5. 其他情况：只保留后4位\n" +
                "    ELSE CONCAT('****', SUBSTR(cast(AES_DECRYPT(unhex(qt.session_customer_phone), 'JBrWVncO9uUlVbhE','ECB') as string), -4)) END as session_customer_phone,\n" +
                "\n" +
                "       qt.session_agent_id                                     as session_agent_id,\n" +
                "       qt.session_agent_code                                   as session_agent_code,\n" +
                "       qt.session_agent_name                                   as session_agent_name,\n" +
                "       qt.session_agent_phone                                  as session_agent_phone,\n" +
                "       qt.session_agent_phone_ext                              as session_agent_phone_ext,\n" +
                "       qt.session_provider_code                                as session_provider_code,\n" +
                "       qt.session_provider_name                                as session_provider_name,\n" +
                "       qt.session_wo_no                                        as session_wo_no,\n" +
                "       qt.session_wo_describe                                  as session_wo_describe,\n" +
                "       case qt.session_wo_group_type\n" +
                "           when 1 then '关闭'\n" +
                "           when 2 then '网点组'\n" +
                "           when 3 then '代理组'\n" +
                "           when 4 then '总部工单组'\n" +
                "           when 5 then '总部理赔组'\n" +
                "           when 6 then '暂不处理'\n" +
                "           when 7 then '催单'\n" +
                "           when 8 then '升级'\n" +
                "           when 9 then '新JT工单组'\n" +
                "           end                                                 as session_wo_group_type,\n" +
                "       case qt.session_wo_operate_type\n" +
                "           when 1 then '关闭'\n" +
                "           when 2 then '转单'\n" +
                "           when 3 then '暂不处理'\n" +
                "           when 4 then '催单'\n" +
                "           when 5 then '升级'\n" +
                "           when 6 then '无'\n" +
                "           end                                                 as session_wo_operate_type,\n" +
                "       case qt.session_wo_transfer\n" +
                "           when 1 then '是'\n" +
                "           when 2 then '否'\n" +
                "           end                                                 as session_wo_transfer,\n" +
                "       qt.session_wo_transfer_type                             as session_wo_transfer_type,\n" +
                "       qt.session_wo_transfer_network_code                     as session_wo_transfer_network_code,\n" +
                "       qt.session_wo_transfer_network_name                     as session_wo_transfer_network_name,\n" +
                "       qt.session_wo_transfer_agency_code                      as session_wo_transfer_agency_code,\n" +
                "       qt.session_wo_transfer_agency_name                      as session_wo_transfer_agency_name,\n" +
                "       qt.session_wo_last_reply_code                           as session_wo_last_reply_code,\n" +
                "       qt.session_wo_last_reply_name                           as session_wo_last_reply_name,\n" +
                "       case qt.session_wo_customer_type\n" +
                "           when 1 then '寄件人'\n" +
                "           when 2 then '收件人'\n" +
                "           when 3 then '其他'\n" +
                "           end                                                 as session_wo_customer_type,\n" +
                "       qt.session_wo_customer_name                             as session_wo_customer_name,\n" +
                "       qt.session_wo_call_back_phone                           as session_wo_call_back_phone,\n" +
                "       qt.session_wo_franchisee_code                           as session_wo_franchisee_code,\n" +
                "       qt.session_wo_franchisee_name                           as session_wo_franchisee_name,\n" +
                "       qt.session_wo_city_id                                   as session_wo_city_id,\n" +
                "       qt.session_wo_city_name                                 as session_wo_city_name,\n" +
                "       qt.session_wo_first_type_code                           as session_wo_first_type_code,\n" +
                "       qt.session_wo_first_type_name                           as session_wo_first_type_name,\n" +
                "       qt.session_wo_second_type_code                          as session_wo_second_type_code,\n" +
                "       qt.session_wo_second_type_name                          as session_wo_second_type_name,\n" +
                "       qt.session_wo_outbound_count                            as session_wo_outbound_count,\n" +
                "       qt.session_wo_is_second_complaint                       as session_wo_is_second_complaint,\n" +
                "       qt.session_wo_logistics_node_type                       as session_wo_logistics_node_type,\n" +
                "       qt.session_wo_operation_site_type                       as session_wo_operation_site_type,\n" +
                "       qt.session_wo_call_number_match                         as session_wo_call_number_match,\n" +
                "       qt.session_wo_close_result                              as session_wo_close_result,\n" +
                "       qt.session_waybill_no                                   as session_waybill_no,\n" +
                "       qt.session_waybill_nos                                  as session_waybill_nos,\n" +
                "       qt.session_waybill_sign_time                            as session_waybill_sign_time,\n" +
                "       qt.session_order_no                                     as session_order_no,\n" +
                "       qt.create_time                                          as create_time,\n" +
                "       case qt.task_source\n" +
                "           when 1 then '在线质检'\n" +
                "           when 2 then '语音质检'\n" +
                "           when 3 then '工单质检'\n" +
                "           end                                                 as task_source,\n" +
                "       case qt.qt_object\n" +
                "           when 1 then '客服'\n" +
                "           when 2 then '兔优达-派件员'\n" +
                "           end                                                 as qt_object,\n" +
                "       case qt.qt_whether_appeal\n" +
                "           when 1 then '是'\n" +
                "           when 2 then '否'\n" +
                "           end                                                 as qt_whether_appeal,\n" +
                "       case qt.qt_whether_rc\n" +
                "           when 1 then '是'\n" +
                "           when 2 then '否'\n" +
                "           end                                                 as qt_whether_rc,\n" +
                "       case qt.session_wo_type\n" +
                "           when 1 then '普通工单'\n" +
                "           when 2 then '理赔工单'\n" +
                "           when 3 then '平台工单'\n" +
                "           when 4 then '问题记录'\n" +
                "           when 5 then '邮政工单'\n" +
                "           when 6 then '邮政申诉工单'\n" +
                "           when 7 then '邮政投诉工单'\n" +
                "           when 8 then '邮政线下工单'\n" +
                "           end                                                 as session_wo_type,\n" +
                "       case qt.session_wo_source\n" +
                "           when 1 then '网点组'\n" +
                "           when 2 then '代理组'\n" +
                "           when 3 then '总部组'\n" +
                "           when 4 then '电话组别'\n" +
                "           when 5 then '总部工单组'\n" +
                "           when 6 then '总部理赔组'\n" +
                "           when 7 then '平台'\n" +
                "           when 8 then '消费者'\n" +
                "           when 9 then '商家'\n" +
                "           when 10 then '邮政'\n" +
                "           end                                                 as session_wo_source,\n" +
                "       case qt.session_wo_channel\n" +
                "           when 1 then '桃花岛'\n" +
                "           when 2 then '紫金山'\n" +
                "           when 3 then '七星潭'\n" +
                "           when 4 then '小红书'\n" +
                "           when 5 then '淘天'\n" +
                "           when 6 then '极地湾'\n" +
                "           when 7 then '视频号'\n" +
                "           when 8 then '快手'\n" +
                "           when 9 then '快递100'\n" +
                "           end                                                 as session_wo_channel,\n" +
                "       case qt.session_wo_whether_postal\n" +
                "           when 1 then '是'\n" +
                "           when 2 then '否'\n" +
                "           end                                                 as session_wo_whether_postal,\n" +
                "       case qt.session_wo_whether_complaints\n" +
                "           when 1 then '是'\n" +
                "           when 2 then '否'\n" +
                "           end                                                 as session_wo_whether_complaints,\n" +
                "       cp_qt.rc_hits_names                                     as rc_hits_names,\n" +
                "       cp_qt.rc_hits_count                                     as rc_hits_count,\n" +
                "       cp_qt.rc_score                                          as rc_score,\n" +
                "       case cp_qt.rc_result\n" +
                "           when 1 then '合格'\n" +
                "           when 2 then '不合格'\n" +
                "           end                                                 as task_detect_result,\n" +
                "       case\n" +
                "           when appeal_recheck_qt.id is not null then '申诉复检'\n" +
                "           when recheck_qt.id is not null then '人工复检'\n" +
                "           else '机器质检'\n" +
                "           end                                                 as task_type,\n" +
                "       recheck_qt.rc_assign_code                               as qt_last_rc_assign_code,\n" +
                "       recheck_qt.rc_assign_name                               as rc_assign_name,\n" +
                "       recheck_qt.rc_assign_time                               as rc_assign_time,\n" +
                "       recheck_qt.rc_op_code                                   as qt_last_rc_op_code,\n" +
                "       recheck_qt.rc_op_name                                   as rc_op_name,\n" +
                "       recheck_qt.rc_op_time                                   as rc_op_time,\n" +
                "       recheck_qt.rc_remark                                    as rc_remark,\n" +
                "       recheck_qt.rc_hits_names                                as rc_hits_names,\n" +
                "       recheck_qt.rc_hits_count                                as rc_hits_count,\n" +
                "       recheck_qt.rc_score                                     as rc_score,\n" +
                "       case recheck_qt.rc_result\n" +
                "           when 1 then '合格'\n" +
                "           when 2 then '不合格'\n" +
                "           end                                                 as rc_result,\n" +
                "    --    case\n" +
                "    --        when appeal_recheck_qt.id is not null\n" +
                "    --            and appeal_recheck_qt.status = 3\n" +
                "    --            then appeal_recheck_qt.rc_hits_group_names\n" +
                "    --        when recheck_qt.id is not null\n" +
                "    --            and recheck_qt.status = 3\n" +
                "    --            then recheck_qt.rc_hits_group_names\n" +
                "    --        when cp_qt.id is not null\n" +
                "    --            and cp_qt.status = 3\n" +
                "    --            then cp_qt.rc_hits_group_names\n" +
                "    --        end                                                 as qt_last_rc_rule_model_group_name_format,\n" +
                "       appeal_recheck_qt.appellant_code                        as qt_last_appellant_code,\n" +
                "       appeal_recheck_qt.appellant_name                        as appellant_name,\n" +
                "       appeal_recheck_qt.appeal_time                           as appeal_time,\n" +
                "       appeal_recheck_qt.appeal_remark                         as appeal_remark,\n" +
                "       appeal_recheck_qt.rc_assign_code                        as qt_last_appeal_rc_assign_code,\n" +
                "       appeal_recheck_qt.rc_assign_name                        as rc_assign_name,\n" +
                "       appeal_recheck_qt.rc_assign_time                        as rc_assign_time,\n" +
                "       appeal_recheck_qt.rc_op_code                            as qt_last_appeal_rc_op_code,\n" +
                "       appeal_recheck_qt.rc_op_name                            as rc_op_name,\n" +
                "       appeal_recheck_qt.rc_op_time                            as rc_op_time,\n" +
                "       appeal_recheck_qt.rc_remark                             as rc_remark,\n" +
                "       appeal_recheck_qt.rc_hits_names                         as rc_hits_names,\n" +
                "       appeal_recheck_qt.rc_hits_count                         as rc_hits_count,\n" +
                "       appeal_recheck_qt.rc_score                              as rc_score,\n" +
                "       case appeal_recheck_qt.rc_result\n" +
                "           when 1 then '合格'\n" +
                "           when 2 then '不合格'\n" +
                "           end                                                 as rc_all_result,\n" +
                "       timestampdiff(second, qt.create_time, qt.qt_close_time) as qt_close_duration_format,\n" +
                "       if(qt.session_wo_whether_return = 1, '是',\n" +
                "          if(qt.session_wo_whether_reboot = 1, '是', '否'))    as session_wo_whether_return_or_reboot,\n" +
                "       if(qt.session_wo_whether_repeat_calls = 1, '是',\n" +
                "          if(qt.session_wo_whether_urge = 1, '是', '否'))      as session_wo_whether_repeat_calls_or_urge,\n" +
                "          qt.sender_phone as sender_phone,\n" +
                "          qt.receiver_phone as receiver_phone,\n" +
                "          case when qt.customer_phone_type = 1 then '寄件电话'\n" +
                "               when qt.customer_phone_type = 2 then '收件电话'\n" +
                "               when qt.customer_phone_type = 3 then '其他'\n" +
                "          end as customer_phone_type,\n" +
                "          qt.session_wo_record_group as session_wo_record_group,\n" +
                "          case when qt.push_object_status = 1 then '是'\n" +
                "               when qt.push_object_status = 2 then '否'\n" +
                "          end as push_object_status,\n" +
                "          qt.session_network_type_id as session_network_type_id,\n" +
                "          case when qt.session_network_logo = 1 then '网点'\n" +
                "               when qt.session_network_logo = 2 then '转运中心' \n" +
                "               when qt.session_network_logo = 3 then '集散点'\n" +
                "          end as session_network_logo,\n" +
                "          qt.labels as labels,\n" +
                "       qt.dt\n" +
                "from jms_dwd.dwd_qt_task_hi qt\n" +
                "         left join (select qt.id                            as id,\n" +
                "                           any_value(qtd.status)            as status,\n" +
                "                           any_value(qtd.rc_score)          as rc_score,\n" +
                "                           any_value(qtd.rc_result)         as rc_result,\n" +
                "                           any_value(qtd.rc_remark)         as rc_remark,\n" +
                "                           count(qtdh.id)                   as rc_hits_count,\n" +
                "                           collect_set(distinct qrm.name)  as rc_hits_names,\n" +
                "                           any_value(qtd.rc_assign_code)    as rc_assign_code,\n" +
                "                           any_value(qtd.rc_assign_name)    as rc_assign_name,\n" +
                "                           any_value(qtd.rc_assign_time)    as rc_assign_time,\n" +
                "                           any_value(qtd.rc_op_code)        as rc_op_code,\n" +
                "                           any_value(qtd.rc_op_name)        as rc_op_name,\n" +
                "                           any_value(qtd.rc_op_time)        as rc_op_time,\n" +
                "                           any_value(qtd.appellant_code)    as appellant_code,\n" +
                "                           any_value(qtd.appellant_name)    as appellant_name,\n" +
                "                           any_value(qtd.appeal_time)       as appeal_time,\n" +
                "                           any_value(qtd.appeal_remark)     as appeal_remark\n" +
                "                    from jms_dwd.dwd_qt_task_hi qt\n" +
                "                             inner join jms_dwd.dwd_qt_task_detect_hi qtd on qtd.task_id = qt.id and qtd.type = 2 \n" +
                "                                        and qtd.dt between date_sub('{{ execution_date | cst_ds }}',60) and '{{ execution_date | cst_ds }}'\n" +
                "                             inner join jms_dwd.dwd_qt_task_detect_hit_hi qtdh on qtdh.task_detect_id = qtd.id\n" +
                "                                        and qtdh.dt between date_sub('{{ execution_date | cst_ds }}',60) and '{{ execution_date | cst_ds }}'\n" +
                "                                       left join jms_dim.dim_qt_rule_model_hi qrm --规则模型\n" +
                "on qrm.id=qtdh.rule_model_id and qrm.is_delete=1 and qrm.is_enable=1\n" +
                "                    where qt.dt between date_sub('{{ execution_date | cst_ds }}',30) and '{{ execution_date | cst_ds }}'\n" +
                "                    group by qt.id) cp_qt on qt.id = cp_qt.id\n" +
                "         left join (select qt.id                            as id,\n" +
                "                           any_value(qtd.status)            as status,\n" +
                "                           any_value(qtd.rc_score)          as rc_score,\n" +
                "                           any_value(qtd.rc_result)         as rc_result,\n" +
                "                           any_value(qtd.rc_remark)         as rc_remark,\n" +
                "                           count(qtdh.id)                   as rc_hits_count,\n" +
                "                           collect_set(distinct qrm.name)  as rc_hits_names,\n" +
                "                           any_value(qtd.rc_assign_code)    as rc_assign_code,\n" +
                "                           any_value(qtd.rc_assign_name)    as rc_assign_name,\n" +
                "                           any_value(qtd.rc_assign_time)    as rc_assign_time,\n" +
                "                           any_value(qtd.rc_op_code)        as rc_op_code,\n" +
                "                           any_value(qtd.rc_op_name)        as rc_op_name,\n" +
                "                           any_value(qtd.rc_op_time)        as rc_op_time,\n" +
                "                           any_value(qtd.appellant_code)    as appellant_code,\n" +
                "                           any_value(qtd.appellant_name)    as appellant_name,\n" +
                "                           any_value(qtd.appeal_time)       as appeal_time,\n" +
                "                           any_value(qtd.appeal_remark)     as appeal_remark\n" +
                "                    from jms_dwd.dwd_qt_task_hi qt\n" +
                "                             inner join jms_dwd.dwd_qt_task_detect_hi qtd on qtd.task_id = qt.id and qtd.type = 3 \n" +
                "                                        and qtd.dt between date_sub('{{ execution_date | cst_ds }}',60) and '{{ execution_date | cst_ds }}'\n" +
                "                             inner join jms_dwd.dwd_qt_task_detect_hit_hi qtdh on qtdh.task_detect_id = qtd.id \n" +
                "                                        and qtdh.dt between date_sub('{{ execution_date | cst_ds }}',60) and '{{ execution_date | cst_ds }}'\n" +
                "                                     left join jms_dim.dim_qt_rule_model_hi qrm --规则模型\n" +
                "on qrm.id=qtdh.rule_model_id and qrm.is_delete=1 and qrm.is_enable=1\n" +
                "where qt.dt between date_sub('{{ execution_date | cst_ds }}',30) and '{{ execution_date | cst_ds }}'\n" +
                "                    group by qt.id) recheck_qt on qt.id = recheck_qt.id\n" +
                "         left join (select qt.id                            as id,\n" +
                "                           any_value(qtd.status)            as status,\n" +
                "                           any_value(qtd.rc_score)          as rc_score,\n" +
                "                           any_value(qtd.rc_result)         as rc_result,\n" +
                "                           any_value(qtd.rc_remark)         as rc_remark,\n" +
                "                           count(qtdh.id)                   as rc_hits_count,\n" +
                "                           collect_set(distinct qrm.name)  as rc_hits_names,\n" +
                "                        --    collect_set(distinct qrmg.name) as rc_hits_group_names,\n" +
                "                           any_value(qtd.rc_assign_code)    as rc_assign_code,\n" +
                "                           any_value(qtd.rc_assign_name)    as rc_assign_name,\n" +
                "                           any_value(qtd.rc_assign_time)    as rc_assign_time,\n" +
                "                           any_value(qtd.rc_op_code)        as rc_op_code,\n" +
                "                           any_value(qtd.rc_op_name)        as rc_op_name,\n" +
                "                           any_value(qtd.rc_op_time)        as rc_op_time,\n" +
                "                           any_value(qtd.appellant_code)    as appellant_code,\n" +
                "                           any_value(qtd.appellant_name)    as appellant_name,\n" +
                "                           any_value(qtd.appeal_time)       as appeal_time,\n" +
                "                           any_value(qtd.appeal_remark)     as appeal_remark\n" +
                "                    from1 jms_dwd.dwd_qt_task_hi qt\n" +
                "                             inner join jms_dwd.dwd_qt_task_detect_hi qtd\n" +
                "                                        on qtd.task_id = qt.id and qtd.type = 4 and qtd.last = 1 \n" +
                "                                        and qtd.dt between date_sub('{{ execution_date | cst_ds }}',60) and '{{ execution_date | cst_ds }}'\n" +
                "                             inner join jms_dwd.dwd_qt_task_detect_hit_hi qtdh on qtdh.task_detect_id = qtd.id \n" +
                "                                        and qtdh.dt between date_sub('{{ execution_date | cst_ds }}',60) and '{{ execution_date | cst_ds }}'\n" +
                "                             left join jms_dim.dim_qt_rule_model_hi qrm --规则模型\n" +
                "on qrm.id=qtdh.rule_model_id and qrm.is_delete=1 and qrm.is_enable=1\n" +
                "where qt.dt between date_sub('{{ execution_date | cst_ds }}',30) and '{{ execution_date | cst_ds }}'\n" +
                "                    group by qt.id) appeal_recheck_qt on qt.id = appeal_recheck_qt.id \n" +
                "                    where qt.dt between date_sub('{{ execution_date | cst_ds }}',30) and '{{ execution_date | cst_ds }}'\n" +
                "                    \n" +
                "                    distribute by qt.dt,1;\n" +
                "\t\t\t\t\t\n" +
                "\t\t\t\t\t";
        SqlScriptProcessor processor = new SqlScriptProcessor();
        List<ProcessedSqlSegment> segments = processor.process(sql);
        System.out.println();
    }
}
