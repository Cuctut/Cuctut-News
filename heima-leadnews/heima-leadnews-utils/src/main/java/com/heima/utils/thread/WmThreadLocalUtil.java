package com.heima.utils.thread;

import com.heima.model.wemedia.pojos.WmUser;

public class WmThreadLocalUtil {
    public static ThreadLocal<WmUser> WM_USER_THREAD_LOCAL = new ThreadLocal<>();

    public static void setUser(WmUser user){
        WM_USER_THREAD_LOCAL.set(user);
    }

    public static WmUser getUser(){
        return WM_USER_THREAD_LOCAL.get();
    }

    public static void clear(){
        WM_USER_THREAD_LOCAL.remove();
    }
}
