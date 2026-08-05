package com.travel.agent.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一返回结果。
 *
 * <p>所有 REST 接口统一返回该结构，便于前端（Web/小程序）处理与错误拦截。
 *
 * @param <T> 数据载荷类型
 */
@Data
@AllArgsConstructor
public class ApiResult<T> {

  /** 业务状态码：0 表示成功，非 0 表示业务错误 */
  private int code;

  /** 提示信息 */
  private String message;

  /** 数据载荷 */
  private T data;

  public static <T> ApiResult<T> success(T data) {
    return new ApiResult<>(0, "success", data);
  }

  public static <T> ApiResult<T> error(int code, String message) {
    return new ApiResult<>(code, message, null);
  }
}
