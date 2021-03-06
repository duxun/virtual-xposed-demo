package io.github.xuqplus.vxp_test01;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import com.alibaba.fastjson.JSON;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Demo03 implements IXposedHookLoadPackage {

    private Map<String, Object> msgs = new HashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException, InstantiationException {
        XposedBridge.log("#### handleLoadPackage executed..");

        logLoadPackageParam(loadPackageParam);

        boolean isAlipaysApp = isThatApp(loadPackageParam, "com.eg.android.AlipayGphone", "支付宝");

        if (isAlipaysApp) {
            final ClassLoader classLoader = loadPackageParam.classLoader;
            XposedHelpers.findAndHookMethod(Application.class, "attach", new Object[]{Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final XC_MethodHook.MethodHookParam param) throws Throwable {
                    XposedBridge.log("#### attach afterHookedMethod");
                    ClassLoader args0 = ((Context) param.args[0]).getClassLoader();
                    XposedBridge.log(String.format("#### attach afterHookedMethod args0=%s", args0));
//                    securityCheckHook(args0);
                    hookRpc(args0);
                    try {
                        final Class classChatMessageProcesser = args0.loadClass("com.alipay.mobile.socialchatsdk.chat.processer.ChatMessageProcesser");
                        final Class classMessageFactory = args0.loadClass("com.alipay.mobile.socialchatsdk.chat.sender.MessageFactory");
                        XposedBridge.log(String.format("#### attach afterHookedMethod classChatMessageProcesser=%s", classChatMessageProcesser.getName()));
                        XposedBridge.log(String.format("#### attach afterHookedMethod classMessageFactory=%s", classMessageFactory.getName()));
                        /* MessageFactory.createTextMsg */
                        XposedHelpers.findAndHookMethod(classMessageFactory, "createTextMsg", new Object[]{String.class, String.class, String.class, String.class, String.class, Boolean.TYPE, new XC_MethodHook() {

                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param1) throws Throwable {
                                XposedBridge.log(String.format("#### createTextMsg beforeHookedMethod param1=%s", Arrays.toString(param1.args)));
                            }
                        }});
                        /* ChatMessageProcesser.processMessages */
                        XposedHelpers.findAndHookMethod(classChatMessageProcesser, "processMessages", new Object[]{List.class, new XC_MethodHook() {


                            @Override
                            protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam param1) throws Throwable {
                                try {
                                    XposedBridge.log(String.format("#### processMessages beforeHookedMethod param1=%s", Arrays.toString(param1.args)));
                                    /* 处理消息 */
                                    Map<String, String> msg = handleMessage(param1.args);
                                    String msgId = msg.get("incrementalId");
                                    if (null != msgs.get(msgId)) {
                                        XposedBridge.log(String.format("#### 该消息已经处理, msgId=%s", msgId));
                                        return;
                                    } else {
                                        msgs.put(msgId, true);
                                    }
                                    /* 是向你付款消息吗 */
                                    if (isPayedToMsg(msg)) {
                                        final String aliTradeNo = msg.get("tradeNO");
                                        final String fromUId = msg.get("fromUId");
                                        final String toUId = msg.get("toUId");
                                        final String bizMemo = msg.get("bizMemo");
                                        XposedBridge.log(String.format("#### isPayedToMsg aliTradeNo=%s, fromUId=%s, toUId=%s, toUId=%s,", aliTradeNo, fromUId, toUId, bizMemo));
                                        final SyncHttpClient client = new SyncHttpClient();
                                        final String url = String.format("http://106.12.80.76:8090/notify?aliTradeNo=%s", aliTradeNo);
                                        client.get(url, new AsyncHttpResponseHandler() {
                                            @Override
                                            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                                String response = new String(responseBody);
                                                XposedBridge.log(String.format("#### %s, onSuccess responseBody=%s", url, response));
                                            }

                                            @Override
                                            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                                String response = new String(responseBody);
                                                XposedBridge.log(String.format("#### %s, onSuccess responseBody=%s", url, response));
                                            }
                                        });
                                    }

                                    /* 是普通消息吗 */
                                    if (isNormalMsg(msg)) {
                                        Object m = ((Map) JSON.parse(msg.get("templateData"))).get("m");
                                        /* 删除好友指令 */
                                        if ("#delete#".equals(m)) {
                                            deleteContact(classLoader, msg.get("fromUId"));
                                        }
                                        /* 收款指令 */
                                        if (m.toString().startsWith("#collect#")) {
                                            Map map = (Map) JSON.parse(m.toString().substring("#collect#".length()));
                                            String loginId = (String) map.get("loginId");
                                            String userId = (String) map.get("userId");
                                            String amount = (String) map.get("amount");
                                            String desc = (String) map.get("desc");
                                            collectMoney(classLoader, loginId, userId, amount, desc);
                                        }
                                    }

                                    /* 是新的好友通知吗 */
                                    if (isNewFriend(msg)) {
                                        final String userId = msg.get("fromUId");

                                        final SyncHttpClient client = new SyncHttpClient();
                                        final String url = String.format("http://106.12.80.76:8090/userId?userId=%s", userId);
                                        client.get(url, new AsyncHttpResponseHandler() {
                                            @Override
                                            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                                String response = new String(responseBody);
                                                XposedBridge.log(String.format("#### %s, onSuccess responseBody=%s", url, response));
                                                Map r = (Map) JSON.parse(response);
                                                if ("0".equals(r.get("code")) && "ok".equals(r.get("msg"))) {
                                                    Map data = (Map) r.get("data");
                                                    final String no = (String) data.get("no");
                                                    final String amount = (String) data.get("amount");
                                                    final String desc = (String) data.get("desc");
                                                    /* 发起收款 */
                                                    // r={"success":true,"transferNo":"20190330200040011100220022837971"}
                                                    Object collectMoney = collectMoney(classLoader, null, userId, amount, desc);
                                                    if (XposedHelpers.getBooleanField(collectMoney, "success")) {
                                                        final Object transferNo = XposedHelpers.getObjectField(collectMoney, "transferNo");
                                                        final SyncHttpClient client1 = new SyncHttpClient();
                                                        final String url1 = String.format("http://106.12.80.76:8090/update?no=%s&aliTradeNo=%s", no, transferNo);
                                                        client1.get(url1, new AsyncHttpResponseHandler() {
                                                            @Override
                                                            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                                                XposedBridge.log(String.format("#### %s, onSuccess responseBody=%s", url1, new String(responseBody)));
                                                            }

                                                            @Override
                                                            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                                                XposedBridge.log(String.format("#### %s, onFailure responseBody=%s", url1, new String(responseBody)));
                                                            }
                                                        });
                                                        /* 删除好友 */
                                                        boolean isDeleted = deleteContact(classLoader, userId);
                                                        // todo, 未完成的任务列表
                                                    }
                                                }
                                            }

                                            @Override
                                            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                                XposedBridge.log(String.format("#### %s, onFailure responseBody=%s", url, new String(responseBody)));
                                            }
                                        });
                                    }
                                } catch (Throwable t) {
                                    t.printStackTrace();
                                }
                            }
                        }});
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }});
        }
    }

    /**
     * 检测向你付款消息
     */
    private boolean isPayedToMsg(Map<String, String> msg) {
        if (null != msg && "8003".equals(msg.get("templateCode")) && null != msg.get("tradeNO")) {
            XposedBridge.log(String.format("#### isPayedToMsg fromUId=%s, fromLoginId=%s, toUId=%s, toLoginId=%s, templateCode=%s, templateData=%s, bizMemo=%s",
                    msg.get("fromUId"), msg.get("fromLoginId"), msg.get("toUId"), msg.get("toLoginId"), msg.get("templateCode"), msg.get("templateData"), msg.get("bizMemo")));
            return true;
        }
        return false;
    }

    /**
     * 检测普通聊天消息
     */
    private boolean isNormalMsg(Map<String, String> msg) {
        if (null != msg && "11".equals(msg.get("templateCode"))) {
            XposedBridge.log(String.format("#### isNormalMsg fromUId=%s, fromLoginId=%s, toUId=%s, toLoginId=%s, templateCode=%s, templateData=%s",
                    msg.get("fromUId"), msg.get("fromLoginId"), msg.get("toUId"), msg.get("toLoginId"), msg.get("templateCode"), msg.get("templateData")));
            return true;
        }
        return false;
    }

    /**
     * 是新添加好友的通知消息吗?
     */
    private boolean isNewFriend(Map<String, String> msg) {
        if (null != msg && "8003".equals(msg.get("templateCode")) && msg.get("templateData").contains("现在可以开始聊天了。")) {
            XposedBridge.log(String.format("#### isNewFriend fromUId=%s, fromLoginId=%s, toUId=%s, toLoginId=%s, templateCode=%s, templateData=%s",
                    msg.get("fromUId"), msg.get("fromLoginId"), msg.get("toUId"), msg.get("toLoginId"), msg.get("templateCode"), msg.get("templateData")));
            return true;
        }
        return false;
    }

    private Map handleMessage(Object[] args) {
        if (null != args) {
            // [incrementalId=1553835554173fromUId=2088702132008225fromLoginId=xuq***@live.cntoUId=2088012758570434toLoginId=nullmsgId=190329125950220675clientMsgId=MRELATION-FRIEND_208870213200822520880127585704341553835590260templateCode=8003templateData={"icon":"","m":"你已经添加了QQ，现在可以开始聊天了。","voiceOverText":""}hintMemo=nullbizMemo=nullbizType=MR-F-ACCegg=MR-F-ACClink=nullcreateTimeMills=1553835590261createTime=nullrecent=nullread=nullaction=4bizRemind=nullmsgIndex=8232f73f6ba7f3b24a6500ec47658165_190329125950220675msgOptType=null]
            Map<String, String> msg = parseMessage(((List) args[0]).get(0).toString());
            return msg;
        }
        return null;
    }

    /**
     * 检查启动的是不是这个app
     */
    private boolean isThatApp(XC_LoadPackage.LoadPackageParam loadPackageParam, String packageName, String appName) {
        if (null != loadPackageParam && packageName.equals(loadPackageParam.packageName)) {
            XposedBridge.log(String.format("#### %s app founded..", appName));
            return true;
        }
        return false;
    }

    /**
     * 打印LoadPackageParam相关信息
     */
    private void logLoadPackageParam(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (null != loadPackageParam) {
            XposedBridge.log(String.format("JSON.toJSONString(loadPackageParam)=%s", JSON.toJSONString(loadPackageParam)));
            XposedBridge.log(String.format("loadPackageParam.appInfo=%s", loadPackageParam.appInfo));
            XposedBridge.log(String.format("loadPackageParam.isFirstApplication=%s", loadPackageParam.isFirstApplication));
            XposedBridge.log(String.format("loadPackageParam.processName=%s", loadPackageParam.processName));
            XposedBridge.log(String.format("loadPackageParam.packageName=%s", loadPackageParam.packageName));
            XposedBridge.log(String.format("loadPackageParam.classLoader=%s", loadPackageParam.classLoader));
        } else {
            XposedBridge.log(String.format("loadPackageParam is null"));
        }
    }

    /**
     * 发起主动收款
     */
    private Object collectMoney(ClassLoader classLoader, String logonId, String userId, String payAmount, String desc) {
        XposedBridge.log(String.format("#### collectMoney, userId=%s, payAmount=%s, desc=%s", userId, payAmount, desc));
        Object o = XposedHelpers.newInstance(XposedHelpers.findClass("com.alipay.android.phone.personalapp.socialpayee.rpc.req.SingleCreateReq", classLoader));
        XposedHelpers.setObjectField(o, "userId", userId);
        XposedHelpers.setObjectField(o, "logonId", null == logonId ? "" : logonId);
        XposedHelpers.setObjectField(o, "payAmount", payAmount);
        XposedHelpers.setObjectField(o, "userName", "");
        XposedHelpers.setObjectField(o, "billName", "个人收款");
        XposedHelpers.setObjectField(o, "source", "chat");
        XposedHelpers.setObjectField(o, "desc", desc);
        // o={"billName":"个人收款","desc":"哈哈, 付早上的饭钱1.24","logonId":"","payAmount":"1.24","source":"chat","userId":"2088702132008225","userName":""}
        XposedBridge.log(String.format("#### collectMoney, o=%s", JSON.toJSONString(o)));
        Object r = XposedHelpers.callMethod(this.collectRpcFac, "createBill", new Object[]{o});
        // r={"success":true,"transferNo":"20190330200040011100220022837971"}
        XposedBridge.log(String.format("#### collectMoney, r=%s", JSON.toJSONString(r)));
        return r;
    }

    /**
     * 删除好友
     */
    private boolean deleteContact(ClassLoader classLoader, String userId) {
        XposedBridge.log(String.format("#### deleteContact, userId=%s", userId));
        Object alipayAccount = XposedHelpers.callMethod(this.aliAccountDaoOp, "getAccountById", userId);
        XposedBridge.log(String.format("#### deleteContact, alipayAccount=%s", alipayAccount));
        // JSON.toJSONString(alipayAccount)=
        // {"account":"340824a05nw.cdb@sina.cn","accountType":"2","active":true,"alipayAccount":true,"area":"青浦区","blacked":false,"displayName":"xuqplus",
        // "displayNickName":"xuqplus","exposedAlipayAccount":"340824a05nw.cdb@sina.cn","
        // extSocialInfo":"{\"age\":\"25\",\"bgImgUrl\":\"\",\"constellation\":\"TX\",\"displayArea\":\"上海 青浦区\",\"height\":\"\",\"income\":\"\",\"interest\":\"\",\"profession\":\"\",\"weight\":\"\"}",
        // "extVersion":1534386419799,"firstAlphaChar":"X","friendStatus":1,"gender":"m","groupMemberCount":0,"headImageUrl":"http://tfs.alipayobjects.com/images/partner/T1zRNCXdtcXXXXXXXX_160X160",
        // "hideFriendMoments":"N","hideRealName":false,"isDelete":false,"isFrom":"account","isTop":false,"loginId":"340824a05nw.cdb@sina.cn","matchedPinyinStr":"XUQPLUS",
        // "mobileMatched":0,"myFriend":true,"name":"许群群","nameExceptGroupNick":"xuqplus","nickName":"xuqplus","notDisturb":false,"notShareMyMoments":"N",
        // "phoneNumber":"","province":"上海","realNameStatus":"Y","realNameVisable":true,"showAsEnterprise":"N","source":"通过好友验证添加","sourceDec":"by_f_v","starFriend":false,
        // "userGrade":"大众会员","userId":"2088012758570434","userType":"1","version":0,"zmCreditText":"","zmCreditUrl":""}
        XposedBridge.log(String.format("#### deleteContact, JSON.toJSONString(alipayAccount)=%s", JSON.toJSONString(alipayAccount)));
        Object o = XposedHelpers.newInstance(XposedHelpers.findClass("com.alipay.mobilerelation.biz.shared.req.HandleRelationReq", classLoader));
        XposedHelpers.setObjectField(o, "targetUserId", userId);
        XposedHelpers.setObjectField(o, "alipayAccount", XposedHelpers.getObjectField(alipayAccount, "account"));
        XposedHelpers.setObjectField(o, "bizType", "2");
        Object r = XposedHelpers.callMethod(XposedHelpers.callMethod(this.relationRpcFac, "getRpcProxy", XposedHelpers.findClass("com.alipay.mobilerelation.biz.shared.rpc.AlipayRelationManageService", classLoader)), "handleRelation", o);
        // {"resultCode":100,"success":true,"toastType":1}
        XposedBridge.log(String.format("#### deleteContact, JSON.toJSONString(r)=%s", JSON.toJSONString(r)));
        return XposedHelpers.getBooleanField(r, "success");
    }

    /**
     * 反反hook
     */
    private void securityCheckHook(ClassLoader classLoader) {
        try {
            Class securityCheckClazz = XposedHelpers.findClass("com.alipay.mobile.base.security.CI", classLoader);
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", String.class, String.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object object = param.getResult();
                    XposedHelpers.setBooleanField(object, "a", false);
                    param.setResult(object);
                    super.afterHookedMethod(param);
                }
            });
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", Class.class, String.class, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return (byte) 1;
                }
            });
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", ClassLoader.class, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return (byte) 1;
                }
            });
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return false;
                }
            });
        } catch (Error | Exception e) {
            e.printStackTrace();
        }
    }

    private void securityCheckHook0(ClassLoader classLoader) {
        try {
            Class clazz = XposedHelpers.findClass("com.alipay.mobile.base.security.CI", classLoader);
            XposedHelpers.findAndHookMethod(clazz, "a", clazz, Activity.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return null;
                }
            });
        } catch (Error | Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 什么rpc
     */
    private Object aliAccountDaoOp = null;
    private Object collectRpcFac = null;
    private Object relationRpcFac = null;

    public void hookRpc(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookConstructor("com.alipay.mobile.socialcommonsdk.bizdata.contact.data.AliAccountDaoOp", classLoader, new Object[]{String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final XC_MethodHook.MethodHookParam param) throws Throwable {
                    aliAccountDaoOp = param.thisObject;
                    XposedBridge.log(String.format("#### hookRpc aliAccountDaoOp set to=%s", aliAccountDaoOp));
                }
            }});
            XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.alipay.mobile.common.rpc.RpcFactory", classLoader), "getRpcProxy", new Object[]{Class.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final XC_MethodHook.MethodHookParam param) throws Throwable {
                    if (null != param && null != param.args && param.args[0].toString().contains("com.alipay.mobilerelation.biz.shared.rpc.AlipayRelationManageService")) {
                        relationRpcFac = param.thisObject;
                        XposedBridge.log(String.format("#### hookRpc relationRpcFac set to=%s", relationRpcFac));
                    }
                }
            }});
            XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.alipay.android.phone.personalapp.socialpayee.ui.SocialPersonalActivity", classLoader), "a", new Object[]{new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final XC_MethodHook.MethodHookParam param) throws Throwable {
                    collectRpcFac = XposedHelpers.getObjectField(param.thisObject, "g");
                    if (null != collectRpcFac) {
                        XposedBridge.log(String.format("#### hookRpc collectRpcFac set .."));
                    }
                }
            }});
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * 解析加好友的消息
     */
    private Map parseMessage(String msg) {
        XposedBridge.log(String.format("#### parseMessage msg=%s", msg));
        String[] as = msg.split("=");
        final Map<String, Object> map = new HashMap() {{
            put("incrementalId", null);
            put("fromUId", null);
            put("fromLoginId", null);
            put("toUId", null);
            put("toLoginId", null);
            put("msgId", null);
            put("clientMsgId", null);
            put("templateCode", null);
            put("templateData", null);
            put("tradeNO", null);
            put("hintMemo", null);
            put("bizMemo", null);
            put("bizType", null);
            put("egg", null);
            put("link", null);
            put("createTimeMills", null);
            put("createTime", null);
            put("recent", null);
            put("read", null);
            put("action", null);
            put("bizRemind", null);
            put("msgIndex", null);
            put("msgOptType", null);
        }};
        for (int i = 0; i < as.length; i++) {
            String s = as[i];
            for (int j = s.length(); j > 0; j--) {
                String key = s.substring(j, s.length());
                if (map.containsKey(key)) {
                    as[i] = s.substring(0, j);
                    map.put(key, i);
                    break;
                }
            }
        }
        Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> kv = (Map.Entry) it.next();
            Object v = kv.getValue();
            if (null == v || "null".equals(v)) v = 0;
            map.put(kv.getKey(), as[(Integer) v + 1]);
        }
        XposedBridge.log(String.format("#### parseMessage map=%s", JSON.toJSONString(map)));
        return map;
    }
}
