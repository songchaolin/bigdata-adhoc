package io.gitee.songchaolin.adhoc.server.aspect;

import io.gitee.songchaolin.adhoc.common.web.Result;
import io.gitee.songchaolin.adhoc.common.web.ResultCodeEnum;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 统一返回包裹 + 全局异常处理（一个 advice 搞定，统一 Result 规约）。
 * <ul>
 *   <li><b>统一包裹</b>：controller 只返回裸业务对象（JobSubmitResponse / IPage 等），这里 {@link #beforeBodyWrite}
 *       统一包成 {@code Result.success(body)}。controller 简洁，不每处手写 Result.success。</li>
 *   <li><b>异常处理</b>：业务异常/参数校验/未知异常 -> {@code Result.error}。
 *       未知异常 {@code log.error(..., e)} 打印堆栈 + 请求 URI，定位来自哪个接口。</li>
 * </ul>
 * 作用域限定 adhoc web 包，避免包到 actuator 等框架端点。
 *
 * <p>注意：controller 不要返回 String（StringHttpMessageConverter 无法序列化 Result 对象）；
 * 返回 DTO/IPage/Boolean/Map 等对象类型均走 Jackson，正常包裹。
 */
@RestControllerAdvice(basePackages = "io.gitee.songchaolin.adhoc.server.web")
public class AdhocResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(AdhocResponseAdvice.class);

    /** 对所有 adhoc controller 返回包裹（String 返回跳过，见类注释）。 */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !String.class.equals(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result) {
            return body;  // 异常处理器已返回 Result，不重复包裹
        }
        return Result.success(body);
    }

    /** 业务异常：返回 FAIL + errorCode: message（业务预期，不打堆栈）。 */
    @ExceptionHandler(AdhocException.class)
    public Result<Void> handleAdhoc(AdhocException e) {
        log.warn("[adhoc business error] uri={} | {}", currentUri(), e.getMessage());
        return Result.error(ResultCodeEnum.FAIL.getCode(), e.getErrorCode().name() + ": " + e.getMessage());
    }

    /** @Valid 参数校验失败：返回 PARAMS_IS_INVALID + 字段错误明细。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getAllErrors().stream()
                .filter(err -> err instanceof FieldError)
                .map(err -> ((FieldError) err).getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining(";"));
        log.warn("[param invalid] uri={} | {}", currentUri(), detail);
        return Result.error(ResultCodeEnum.PARAMS_IS_INVALID.getCode(), ResultCodeEnum.PARAMS_IS_INVALID.getMsg() + ":" + detail);
    }

    /**
     * 未知异常：打印堆栈 + 接口 URI 定位来源；code=SYSTEM_INNER_ERROR(600007) 标记类别，
     * msg 直接给真实异常摘要（类名: message），不叠「系统内部错误」前缀（类别已由 code 表达）。
     * 内部平台：前端/运维看到真实原因；完整堆栈仍只在服务端日志，不回传客户端。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("[unexpected error] uri={} | {}", currentUri(), e.getMessage(), e);
        String msg = e.getMessage();
        String detail = e.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
        return Result.error(ResultCodeEnum.SYSTEM_INNER_ERROR.getCode(), detail);
    }

    private String currentUri() {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr != null) {
                HttpServletRequest req = attr.getRequest();
                return req.getMethod() + " " + req.getRequestURI();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "-";
    }
}
