package com.webox.common;

/** Per-request state; always cleared by RequestIdFilter in a finally block. */
public final class RequestContext {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<CurrentUser> USER = new ThreadLocal<>();

    private RequestContext() {}

    public static void init(String requestId) { REQUEST_ID.set(requestId); }
    public static void clear() { REQUEST_ID.remove(); USER.remove(); }
    public static String requestId() {
        String id = REQUEST_ID.get();
        return id == null ? "n/a" : id;
    }
    public static void setUser(CurrentUser user) { USER.set(user); }

    public static CurrentUser user() {
        CurrentUser u = USER.get();
        if (u == null) throw new ApiException(ErrorCode.UNAUTHORIZED, "Please sign in to continue.");
        return u;
    }

    public static Long userId() { return user().id(); }
}
