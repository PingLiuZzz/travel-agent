package com.travel.agent.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>携带业务错误码，由全局异常处理器统一转成 ApiResult 返回前端。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
