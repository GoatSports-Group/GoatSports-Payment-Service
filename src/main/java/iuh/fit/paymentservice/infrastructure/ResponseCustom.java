package iuh.fit.paymentservice.infrastructure;

import iuh.fit.paymentservice.application.dto.response.base.BaseResponse;
import iuh.fit.paymentservice.infrastructure.annotation.ApiMessage;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class ResponseCustom implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    @Nullable
    public Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        HttpServletResponse httpResponse = ((ServletServerHttpResponse) response).getServletResponse();
        int statusCode = httpResponse.getStatus();

        if (body instanceof String) {
            return body;
        }

        String path = request.getURI().getPath();
        if (path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator")) {
            return body;
        }

        if (statusCode >= 400 || body instanceof BaseResponse<?>) {
            return body;
        }

        BaseResponse<Object> baseResponse = new BaseResponse<>();
        baseResponse.setStatusCode(statusCode);
        baseResponse.setData(body);
        ApiMessage apiMessage = returnType.getMethodAnnotation(ApiMessage.class);
        baseResponse.setMessage(apiMessage != null ? apiMessage.value() : "Success");
        return baseResponse;
    }
}
