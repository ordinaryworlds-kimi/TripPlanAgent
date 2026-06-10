package managerAgent.util;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * 超时与远程调用异常识别工具。
 * <p>
 * 用于将底层异常（Reactor、HttpClient、Spring MVC 等）转换为用户可读的中文提示，
 * 供 {@code RemoteAgentTool}、{@code ChatSessionService} 等统一处理。
 */
public final class TimeoutUtil {

    /** 通用超时提示，不区分具体环节时使用 */
    public static final String GENERIC_TIMEOUT_MESSAGE =
            "当前环节执行超时（超过5分钟），请稍后重试或简化需求。";

    private TimeoutUtil() {
    }

    /**
     * 生成带环节名称的超时提示，例如「路线制定超过5分钟」。
     *
     * @param step 环节名称，如「路线制定」「行程规划」
     * @return 面向用户的中文超时消息
     */
    public static String stepTimeoutMessage(String step) {
        return "当前环节执行超时（" + step + "超过5分钟），请稍后重试或简化需求。";
    }

    /**
     * 生成 A2A 远程 Agent 调用失败时的提示。
     * <p>
     * 常见于子 Agent 未启动、LLM 配置错误或 HTTP 连接被提前关闭。
     *
     * @param step 环节名称，如「路线制定」「行程规划」
     * @return 面向用户的远程调用失败消息
     */
    public static String remoteCallErrorMessage(String step) {
        return step + " Agent 远程调用失败：连接被中断或服务异常，请确认对应子 Agent 已启动且 LLM 配置正确后重试。";
    }

    /**
     * 判断异常链中是否包含远程 HTTP 连接中断类错误。
     * <p>
     * 典型场景：A2A 分块传输（chunked）未完成时收到 EOF，或连接被 reset。
     *
     * @param ex 待检查的异常（会沿 {@code getCause()} 向下遍历）
     * @return 若属于远程连接类错误则返回 {@code true}
     */
    public static boolean isRemoteCallError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof EOFException) {
                return true;
            }
            if (current instanceof IOException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("chunked transfer encoding")
                        || lower.contains("eof reached while reading")
                        || lower.contains("connection reset")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 判断异常链中是否包含超时类错误。
     * <p>
     * 覆盖：{@link TimeoutException}、线程中断、Spring {@code AsyncRequestTimeout}，
     * 以及消息中含 timeout / pt5m 等关键字的情况。
     *
     * @param ex 待检查的异常（会沿 {@code getCause()} 向下遍历）
     * @return 若属于超时则返回 {@code true}
     */
    public static boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            if (current instanceof InterruptedException) {
                return true;
            }
            String typeName = current.getClass().getSimpleName();
            if (typeName.contains("AsyncRequestTimeout")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("timeout")
                        || lower.contains("pt5m")
                        || lower.contains("timed out")
                        || message.contains("当前环节执行超时")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 将任意异常解析为适合展示给用户的中文消息。
     * <p>
     * 优先级：超时 &gt; 远程连接中断 &gt; 原始消息 &gt; 通用失败提示。
     *
     * @param ex 捕获到的异常
     * @return 用户可读的错误描述
     */
    public static String resolveMessage(Throwable ex) {
        if (isTimeout(ex)) {
            return GENERIC_TIMEOUT_MESSAGE;
        }
        if (isRemoteCallError(ex)) {
            return "远程 Agent 连接中断，请确认子 Agent 服务与 LLM 配置正常后重试。";
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "执行失败，请稍后重试。" : message;
    }
}
