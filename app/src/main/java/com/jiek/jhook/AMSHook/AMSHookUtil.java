package com.jiek.jhook.AMSHook;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.ArrayMap;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;

//hook 至 ActivityThread，进行拦截 mH 的消息处理中
public class AMSHookUtil {

    @SuppressLint("StaticFieldLeak")
    private static Context mApplicationContext;
    private static String mPackageName;
    private static String mHostClazzName;
    private static final String TAG = "AMSHookUtil";

    /**
     * 通过反射获取到AMS的代理本地代理对象
     * Hook以后动态串改Intent为已注册的来躲避检测
     *
     * @param context 上下文
     */
    public static void hookStartActivity(Context context) {
        if (context == null || mApplicationContext != null) {
            return;
        }
        try {
            mApplicationContext = context.getApplicationContext();
            PackageManager manager = mApplicationContext.getPackageManager();
            mPackageName = mApplicationContext.getPackageName();
            PackageInfo packageInfo = manager.getPackageInfo(mPackageName, PackageManager.GET_ACTIVITIES);
            ActivityInfo[] activities = packageInfo.activities;
            if (activities == null || activities.length == 0) {
                return;
            }
            Log.d(TAG, "遍历宿主注册的 Activity 清单");
            for (ActivityInfo activity : activities) {
                Log.d(TAG, activity.name);
            }
            ActivityInfo activityInfo = activities[0];
            mHostClazzName = activityInfo.name;
            Log.e(TAG, "packageName:" + mPackageName + "\tHostClazzName:" + mHostClazzName);

            compatFetchAMN(context);
            hookLaunchActivity();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 兼容的在ActivityManager中 hook 住InvocationHandler。
     *
     * @param context
     * @throws ClassNotFoundException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private static void compatFetchAMN(Context context) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Log.e(TAG, "compatFetchAMN 系统版本: " + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
//            api <= 25，获取 ActivityManager 方式
            Class<?> amnClazz = Class.forName("android.app.ActivityManagerNative");
            Field defaultField = amnClazz.getDeclaredField("gDefault");
            defaultField.setAccessible(true);
            Object gDefaultObj = defaultField.get(null); //所有静态对象的反射可以通过传null获取。如果是实列必须传实例
            Class<?> singletonClazz = Class.forName("android.util.Singleton");
            Field amsField = singletonClazz.getDeclaredField("mInstance");
            amsField.setAccessible(true);
            Object amsObj = amsField.get(gDefaultObj);

            amsObj = Proxy.newProxyInstance(context.getClass().getClassLoader(),
                    amsObj.getClass().getInterfaces(),
                    new HookInvocationHandler(amsObj, mPackageName, mHostClazzName));
            amsField.set(gDefaultObj, amsObj);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
//            e.printStackTrace();
//            api <= 27，获取ActivityManager 方式
            Class<?> amClazz = Class.forName("android.app.ActivityManager");
            Field defaultField = amClazz.getDeclaredField("IActivityManagerSingleton");
            defaultField.setAccessible(true);
            Object gDefaultObj = defaultField.get(null); //所有静态对象的反射可以通过传null获取。如果是实列必须传实例
            Class<?> singletonClazz = Class.forName("android.util.Singleton");
            Field amsField = singletonClazz.getDeclaredField("mInstance");
            amsField.setAccessible(true);
            Object amsObj = amsField.get(gDefaultObj);

            amsObj = Proxy.newProxyInstance(context.getClass().getClassLoader(),
                    amsObj.getClass().getInterfaces(),
                    new HookInvocationHandler(amsObj, mPackageName, mHostClazzName));
            amsField.set(gDefaultObj, amsObj);

        } else {
            throw new IllegalAccessException("系统版本 >= 28（Build.VERSION_CODES.P），系统黑名单反射挂钩子失效");
        }
    }

    /**
     * hook住ActivityThread，在 mH 中替换 mCallback，在使用未注册的Activity Intent
     *
     * @throws Exception
     */
    private static void hookLaunchActivity() throws Exception {
        Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");

        Object currentActivityThread = compatGetActivityThread(activityThreadClazz);
        if (currentActivityThread == null) {
            //当 hook 失败时，需要兼容，以使正常的功能继续可用。
            return;
        }

        Field mHField = activityThreadClazz.getDeclaredField("mH");
        mHField.setAccessible(true);
        Handler mH = (Handler) mHField.get(currentActivityThread);
        Field callBackField = Handler.class.getDeclaredField("mCallback");
        callBackField.setAccessible(true);
        callBackField.set(mH, new ActivityThreadHandlerCallBack());
    }

    private static class ActivityThreadHandlerCallBack implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            int LAUNCH_ACTIVITY = 0;
            try {
                Class<?> clazz = Class.forName("android.app.ActivityThread$H");
                Field field = clazz.getField("LAUNCH_ACTIVITY");
                LAUNCH_ACTIVITY = field.getInt(null);
            } catch (Exception e) {
            }
            if (msg.what == LAUNCH_ACTIVITY) {
                handleLaunchActivity(msg);
            }
            return false;
        }
    }

    private static void handleLaunchActivity(Message msg) {
        try {
            Object obj = msg.obj;
            Field intentField = obj.getClass().getDeclaredField("intent");
            intentField.setAccessible(true);
            Intent proxyIntent = (Intent) intentField.get(obj);

            Bundle extras = proxyIntent.getExtras();

            traverseBundlePrint(extras);

            //拿到之前真实要被启动的Intent 然后把Intent换掉
            Intent originallyIntent = proxyIntent.getParcelableExtra("originallyIntent");
            if (originallyIntent == null) {
                return;
            }

            proxyIntent.setComponent(originallyIntent.getComponent());

            Log.e(TAG, "handleLaunchActivity:" + originallyIntent.getComponent().getClassName());

            Class<?> forName = Class.forName("android.app.ActivityThread");
            Object activityThread = compatGetActivityThread(forName);

            Method getPackageManager = activityThread.getClass().getDeclaredMethod("getPackageManager");
            Object iPackageManager = getPackageManager.invoke(activityThread);

            //用代理实例挂钩子
            Object proxy = Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class<?>[]{Class.forName("android.content.pm.IPackageManager")},
                    new MyInvocationHandler(iPackageManager));

            // 获取 sPackageManager 属性
            Field iPackageManagerField = activityThread.getClass().getDeclaredField("sPackageManager");
            iPackageManagerField.setAccessible(true);
            iPackageManagerField.set(activityThread, proxy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 两种方式遍历 bundle 的数据，调试功能
     *
     * @param extras
     * @throws NoSuchFieldException
     */
    private static void traverseBundlePrint(Bundle extras) throws NoSuchFieldException {
        Class<?> sclazz = extras.getClass().getSuperclass();
        Log.e(TAG, "handleLaunchActivity: " + sclazz.getName());
        Field mMap = sclazz.getDeclaredField("mMap");
        mMap.setAccessible(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                ArrayMap<String, Object> map = (ArrayMap<String, Object>) mMap.get(extras);
                Iterator<String> iterator = map.keySet().iterator();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    Object val = map.get(key);
                    System.out.println("bundle type 1: key= " + key + ": " + val + " [" + val.getClass().getName() + "]");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        Iterator<String> iterator = extras.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object val = extras.get(key);
            System.out.println("bundle type 2: key= " + key + ": " + val + " [" + val.getClass().getName() + "]");
        }
    }

    /**
     * 兼容高低版本 api，通过反射 ActivityThread 获取实例
     */
    private static Object compatGetActivityThread(Class<?> forNameActivityThread) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
//        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
////            api <= 24
//            Field field = forNameActivityThread.getDeclaredField("sCurrentActivityThread");
//            field.setAccessible(true);
//            return field.get(null);
//        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
//         api <= 28
        return forNameActivityThread.getDeclaredMethod("currentActivityThread", null).invoke(null, null);
//        }
//        return null;
    }

    private static class MyInvocationHandler implements InvocationHandler {
        private Object mActivityManagerObject;

        MyInvocationHandler(Object mActivityManagerObject) {
            this.mActivityManagerObject = mActivityManagerObject;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getActivityInfo")) {
                ComponentName componentName = new ComponentName(mPackageName, mHostClazzName);
                args[0] = componentName;
            }
            return method.invoke(mActivityManagerObject, args);
        }
    }

    private static class HookInvocationHandler implements InvocationHandler {

        private Object mAmsObj;
        private String mPackageName;
        private String cls;

        public HookInvocationHandler(Object amsObj, String packageName, String cls) {
            this.mAmsObj = amsObj;
            this.mPackageName = packageName;
            this.cls = cls;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // hook startActivity方法
            if (method.getName().equals("startActivity")) {
                int index = -1;
                // 遍历参数找到 intent
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Intent) {
                        index = i;
                        break;
                    }
                }
                if (index >= 0) {
                    Intent originallyIntent = (Intent) args[index];
                    Log.i("HookInvocationHandler", originallyIntent.getComponent().getClassName());
                    // 用主 Activity（在 AndroidManifest 中注册过）代理新出Intent
                    Intent proxyIntent = new Intent();
                    proxyIntent.setComponent(new ComponentName(mPackageName, cls));
                    // 偷梁换柱，组件用hook时的 Activity，启动的Activity 还用原 Activity
                    proxyIntent.putExtra("originallyIntent", originallyIntent);
                    args[index] = proxyIntent;
                }
            }
            return method.invoke(mAmsObj, args);
        }
    }
}
