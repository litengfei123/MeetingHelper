package com.ceiv.meetinghelper.ui;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.alibaba.fastjson.JSON;
import com.ceiv.meetinghelper.R;
import com.ceiv.meetinghelper.adapter.MeetingAdapter;
import com.ceiv.meetinghelper.bean.MqttMeetingCurrentBean;
import com.ceiv.meetinghelper.bean.MqttMeetingListBean;
import com.ceiv.meetinghelper.control.CodeConstants;
import com.ceiv.meetinghelper.greendao.DaoMaster;
import com.ceiv.meetinghelper.greendao.DaoSession;
import com.ceiv.meetinghelper.greendao.MqttMeetingListBeanDao;
import com.ceiv.meetinghelper.listener.CurMeetingCallBack;
import com.ceiv.meetinghelper.listener.FragmentCallBackA;
import com.ceiv.meetinghelper.listener.FragmentCallBackB;
import com.ceiv.meetinghelper.listener.FragmentCallBackC;
import com.ceiv.meetinghelper.listener.FragmentCallBackD;
import com.ceiv.meetinghelper.listener.MeetingEndListener;
import com.ceiv.meetinghelper.listener.MeetingGoingListener;
import com.ceiv.meetinghelper.listener.MeetingOverListener;
import com.ceiv.meetinghelper.listener.MeetingStartListener;
import com.ceiv.meetinghelper.listener.NetEvevtListener;
import com.ceiv.meetinghelper.listener.RoomNumChangeListener;
import com.ceiv.meetinghelper.listener.TodayMeetingCallBack;
import com.ceiv.meetinghelper.log4j.LogUtils;
import com.ceiv.meetinghelper.utils.ApkUtils;
import com.ceiv.meetinghelper.utils.DateTimeUtil;
import com.ceiv.meetinghelper.utils.LogUtil;
import com.ceiv.meetinghelper.utils.NetBroadcastReceiver;
import com.ceiv.meetinghelper.utils.NetUtil;
import com.ceiv.meetinghelper.utils.RequestApi;
import com.ceiv.meetinghelper.utils.SharePreferenceManager;
import com.ceiv.meetinghelper.utils.SharedPreferenceTools;
import com.ceiv.meetinghelper.utils.ToastUtils;
import com.ceiv.meetinghelper.utils.Utils;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.lzy.okgo.request.GetRequest;
import com.lzy.okserver.OkDownload;
import com.lzy.okserver.download.DownloadListener;
import com.lzy.okserver.download.DownloadTask;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements CurMeetingCallBack, TodayMeetingCallBack ,NetEvevtListener
, MeetingStartListener, MeetingGoingListener, MeetingOverListener, MeetingEndListener, RoomNumChangeListener ,
        CustomEidtDialog.OnOffClickListener, CustomEidtDialog.OnSetClickListener {
    protected String TAG = getClass().getSimpleName();
    private static FragmentCallBackA fragmentCallBackA;
    private static FragmentCallBackB fragmentCallBackB;
    private static FragmentCallBackC fragmentCallBackC;
    private static FragmentCallBackD fragmentCallBackD;
    private List<android.support.v4.app.Fragment> frags = new ArrayList<>();

    private LauncherActivity launcherActivity = new LauncherActivity();
    private AFragment aFragment = new AFragment();
    private BFragment bFragment = new BFragment();
    private CFragment cFragment = new CFragment();
    private DFragment dFragment = new DFragment();
    private ViewPager viewPager;
    private MyViewPagerAdapter pagerAdapter;
    private TextView room, cur, today, news_cur, news_today, download;
    private EditText editText;
    private int templateId;// 0 代表模板A   1代表模板2
    private List<MqttMeetingListBean> meetingListQuery = new ArrayList<>();
    private List<MqttMeetingListBean> meetingListReceive = new ArrayList<>();
    private List<MqttMeetingCurrentBean> curMeeting = new ArrayList<>();
    private MeetingAdapter adapter = null;
    private static String versionCodeOnLine, appUrl,meetingRoomName;
    private int versionCodeLocal;
    private Intent intent;
    private DateTimeUtil dateTimeUtil;
    private static String roomNum;//会议室编号
    private DaoMaster daoMaster;
    private DaoSession daoSession;
    private MqttMeetingListBeanDao meetingListBeanDao;
    private MqttMeetingListBean mqttMeetingListBean;
    private Timer checkNetTimer;
    private CheckNetTask checkNetTask;
    private long periodTime = 1000*8;//8s
    //以下用于管理息屏和亮屏
    private DevicePolicyManager policyManager;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private ComponentName adminReceiver;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0x1://开始前5分钟
                    viewPager.setCurrentItem(0);
                    break;
                case 0x2://会议中
                    viewPager.setCurrentItem(1);
                    BFragment.check();
                    break;
                case 0x3://会议结束前5分钟
                    viewPager.setCurrentItem(2);
                    break;
                case 0x4://开机默认显示/会议结束
                    viewPager.setCurrentItem(3);
                    checkScreenOff(null);
                    break;
                case 0x33:
                    Toast.makeText(MainActivity.this,"网络连接不可用，请检查网络配置",Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 去除通知栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 去除标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        dateTimeUtil = DateTimeUtil.getInstance();
        adminReceiver = new ComponentName(MainActivity.this, ScreenOffAdminReceiver.class);
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        policyManager = (DevicePolicyManager) MainActivity.this.getSystemService(Context.DEVICE_POLICY_SERVICE);
        checkAndTurnOnDeviceManager(null);
        getStuDao();
        viewPager = findViewById(R.id.viewPager);
        frags.add(aFragment);
        frags.add(bFragment);
        frags.add(cFragment);
        frags.add(dFragment);
        pagerAdapter = new MyViewPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(4);
        checkScreenOn(null);
        viewPager.setCurrentItem(3);
        //删除数据库中今天之前的会议信息
        List<MqttMeetingListBean> userList = meetingListBeanDao.queryBuilder()
                .where(MqttMeetingListBeanDao.Properties.StartDate.lt(dateTimeUtil.transDataToTime(dateTimeUtil.getCurrentDateYYMMDD() + " 00:00:00")))
                .build().list();
        for (MqttMeetingListBean user : userList) {
            meetingListBeanDao.delete(user);
        }
        inspectNet();//检查当前网络状态
        NetBroadcastReceiver.setNetEvevtListener(this);
        MqttService.setCurMeetingCallBack(this);
        MqttService.setTodayMeetingCallBack(this);
        MqttService.setMeetingStartListener(this);
        MqttService.setMeetingGoingListener(this);
        MqttService.setMeetingOverListener(this);
        MqttService.setMeetingEndListener(this);
        CustomEidtDialog.setOnRoomNumChangeListener(this);
        //开启服务
        if (!isServiceRunning(String.valueOf(MqttService.class))) {
            intent = new Intent(this, MqttService.class);
            startService(intent);
            LogUtils.d(TAG, "开启 Mqtt 服务");
        } else {
            LogUtils.d(TAG, "===服务正在运行===");
        }
        //定时检查版本更新
       new Timer().schedule(new TimerTask() {
           @Override
           public void run() {
//               checkVersion();
           }
       }, 1000 * 60 * 30, 1000 * 60 * 60);
    }

    /**
     * @param view 检测并去激活设备管理器权限
     */
    public void checkAndTurnOnDeviceManager(View view) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminReceiver);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "开启后就可以使用锁屏功能了...");
        startActivityForResult(intent, 0);
    }

    /**
     * @param view 熄屏
     */
    public  void checkScreenOff(View view) {
        boolean admin = policyManager.isAdminActive(adminReceiver);
        if (admin) {
            LogUtils.i(TAG,"息屏");
            policyManager.lockNow();
        } else {
            ToastUtils.showToast(this,"没有设备管理权限");
        }
    }
    /**
     * @param view 亮屏
     */
    public void checkScreenOn(View view) {
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        mWakeLock.acquire(1);
        mWakeLock.release();
    }

    public  Handler getMainHandle( ){
        return  handler;
    }
    /**
     * 获取StudentDao
     */
    private void getStuDao() {
        // 创建数据
        DaoMaster.DevOpenHelper devOpenHelper = new DaoMaster.DevOpenHelper(this, "meetingHelper.db", null);
        daoMaster = new DaoMaster(devOpenHelper.getWritableDb());
        daoSession = daoMaster.newSession();
        meetingListBeanDao = daoSession.getMqttMeetingListBeanDao();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        isOpen();
    }
    private void isOpen() {
        if (policyManager.isAdminActive(adminReceiver)) {//判断超级管理员是否激活
            ToastUtils.showToast(this,"设备已被激活");
        } else {
            ToastUtils.showToast(this,"设备没有被激活");
        }
    }
    /**
     * 会议开始后的前5分钟
     */
    @Override
    public void setDataMeetingStart(String topic, String strMessage) {
        checkScreenOn(null);
        LogUtils.i(TAG, "topic:" + topic + ";----strMessage:" + strMessage);
        Message msg = new Message();
        msg.what = 0x1;
        handler.sendMessage(msg);
        fragmentCallBackA.TransDataA(topic,null);
    }
    /**
     * 会议进行的过程中
     */
    @Override
    public void setDataMeetingGoing(String topic, String strMessage) {
        checkScreenOn(null);
        LogUtils.i(TAG, "topic:" + topic + ";----strMessage:" + strMessage);
        Message msg = new Message();
        msg.what = 0x2;
        handler.sendMessage(msg);

        if (!"".equals(strMessage) && !"[]".equals(strMessage) && strMessage != null && !TextUtils.isEmpty(strMessage)) {
            templateId = SharePreferenceManager.getMeetingMuBanType();//读取存储的模板类型
            curMeeting.clear();
            curMeeting.addAll(JSON.parseArray(strMessage, MqttMeetingCurrentBean.class));
            LogUtils.i(TAG, "===curMeeting   topic:" + topic + ";----curMeeting:" + curMeeting.toString());
            fragmentCallBackB.TransDataB(topic, curMeeting);
        }
    }
    /**
     * 会议进行的最后5分钟
     */
    @Override
    public void setDataMeetingOver(String topic, String strMessage) {
        checkScreenOn(null);
        LogUtils.i(TAG, "topic:" + topic + ";----strMessage:" + strMessage);
        Message msg = new Message();
        msg.what = 0x3;
        handler.sendMessage(msg);

        LogUtils.i(TAG, "topic:" + topic + ";----strMessage:" + strMessage);
        if (!"".equals(strMessage) && !"[]".equals(strMessage) && strMessage != null && !TextUtils.isEmpty(strMessage)) {
            templateId = SharePreferenceManager.getMeetingMuBanType();//读取存储的模板类型
            curMeeting.clear();
            curMeeting.addAll(JSON.parseArray(strMessage, MqttMeetingCurrentBean.class));
            LogUtils.i(TAG, "===curMeeting   topic:" + topic + ";----curMeeting:" + curMeeting.toString());
            fragmentCallBackC.TransDataC(topic, curMeeting);
        }
    }
    /**
     * 会议结束的时刻
     */
    @Override
    public void setDataMeetingEnd(String topic, String strMessage) {
        LogUtils.i(TAG, "topic:" + topic + ";----strMessage:" + strMessage);
        Message msg = new Message();
        msg.what = 0x4;
        handler.sendMessage(msg);
    }

    /**
     * 当前会议数据
     */
    @Override
    public void setDataCur(String topic, String strMessage) {
        LogUtils.i(TAG, "topic:" + topic + ";----strMessage:" + strMessage);
        if (!"".equals(strMessage) && !"[]".equals(strMessage) && strMessage != null && !TextUtils.isEmpty(strMessage)) {
            templateId = SharePreferenceManager.getMeetingMuBanType();//读取存储的模板类型
            curMeeting.clear();
            curMeeting.addAll(JSON.parseArray(strMessage, MqttMeetingCurrentBean.class));
            LogUtils.i(TAG, "===curMeeting   topic:" + topic + ";----curMeeting:" + curMeeting.toString());
            fragmentCallBackB.TransDataB(topic, curMeeting);
        }
    }
    /**
     * 预约会议数据
     */
    @Override
    public void setDataToday(String topic, String strMessage) {
        LogUtils.i(TAG, "topic:" + topic + ";----strMessage:" + strMessage);
        if (!"".equals(strMessage) && !"[]".equals(strMessage) && strMessage != null && !TextUtils.isEmpty(strMessage)) {
            meetingListReceive.clear();
            meetingListReceive.addAll(JSON.parseArray(strMessage, MqttMeetingListBean.class));
//            LogUtils.i(TAG, "===meetingList :" + topic + ";----meetingListReceive:" + meetingListReceive.toString());
            templateId = meetingListReceive.get(0).getTemplateId();
            meetingRoomName = meetingListReceive.get(0).getRoomName();
            SharedPreferenceTools.putValuetoSP(this, CodeConstants.MEETING_ROOM_NAME,meetingRoomName);
            SharePreferenceManager.setMeetingMuBanType(templateId);//将模板类型存到本地缓存中
            roomNum = (String) SharedPreferenceTools.getValueofSP(this, "DeviceNum", "");//获取会议室编号

            switch (meetingListReceive.get(0).getSign()) {
                case "insert":
                    try {
                        mqttMeetingListBean = new MqttMeetingListBean(null,
                                meetingListReceive.get(0).getId(),
                                roomNum,
                                meetingListReceive.get(0).getRoomName(),
                                meetingListReceive.get(0).getDepartment(),
                                meetingListReceive.get(0).getName(),
                                meetingListReceive.get(0).getIsOpen(),
                                meetingListReceive.get(0).getEndDate(),
                                meetingListReceive.get(0).getStartDate(),
                                meetingListReceive.get(0).getTemplateId(),
                                meetingListReceive.get(0).getBookPerson(),
                                meetingListReceive.get(0).getBookPersonPhone(),
                                meetingListReceive.get(0).getSign())
                        ;
                        meetingListBeanDao.insert(mqttMeetingListBean);
                    } catch (Exception e) {
                        e.printStackTrace();
                        LogUtils.w(TAG, "===Exception:插入失败" + e.getMessage());
                    }
                    break;
                case "delete":
                    mqttMeetingListBean = meetingListBeanDao.queryBuilder().where(MqttMeetingListBeanDao.Properties.Id.eq(meetingListReceive.get(0).getId())).build().unique();
                    if (mqttMeetingListBean != null) {
                        meetingListBeanDao.delete(mqttMeetingListBean);
                    }
                    break;
                case "update":
                    mqttMeetingListBean = meetingListBeanDao.queryBuilder().where(MqttMeetingListBeanDao.Properties.Id.eq(meetingListReceive.get(0).getId())).build().unique();
                    if (mqttMeetingListBean != null) {
                        meetingListBeanDao.delete(mqttMeetingListBean);
                    }
                    mqttMeetingListBean = new MqttMeetingListBean(null,
                            meetingListReceive.get(0).getId(),
                            roomNum,
                            meetingListReceive.get(0).getRoomName(),
                            meetingListReceive.get(0).getDepartment(),
                            meetingListReceive.get(0).getName(),
                            meetingListReceive.get(0).getIsOpen(),
                            meetingListReceive.get(0).getEndDate(),
                            meetingListReceive.get(0).getStartDate(),
                            meetingListReceive.get(0).getTemplateId(),
                            meetingListReceive.get(0).getBookPerson(),
                            meetingListReceive.get(0).getBookPersonPhone(),
                            meetingListReceive.get(0).getSign())
                    ;
                    meetingListBeanDao.insert(mqttMeetingListBean);
                    break;
            }
            meetingListQuery.clear();
            meetingListQuery.addAll(meetingListBeanDao.queryBuilder().where(MqttMeetingListBeanDao.Properties.RoomNum.eq(roomNum), MqttMeetingListBeanDao.Properties.StartDate

                    .ge(dateTimeUtil.transDataToTime(dateTimeUtil.getCurrentDateYYMMDD() + " 00:00:00")))
//                    .between(dateTimeUtil.transDataToTime(dateTimeUtil.getCurrentDateYYMMDD() + " 00:00:00"), dateTimeUtil.transDataToTime(dateTimeUtil.getCurrentDateYYMMDD() + " 23:59:59")))
                    .orderAsc(MqttMeetingListBeanDao.Properties.EndDate)

                    .build().list());
            LogUtils.i(TAG, "topic:" + topic + ";----meetingListQuery:" + meetingListQuery.toString());
            fragmentCallBackB.TransDataB(topic, meetingListQuery);
        }
    }

    @Override
    public void setChangedRoomNum(String roomNum) {
        LogUtils.i(TAG, "会议室编号改变为：" + roomNum);
        aFragment.whenRoomNumChanged(roomNum);
        bFragment.whenRoomNumChanged(roomNum);
        cFragment.whenRoomNumChanged(roomNum);
    }

    /**
     * 判断服务是否运行
     */
    private boolean isServiceRunning(final String className) {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> info = activityManager.getRunningServices(Integer.MAX_VALUE);
        if (info == null || info.size() == 0) return false;
        for (ActivityManager.RunningServiceInfo aInfo : info) {
            if (className.equals(aInfo.service.getClassName())) return true;
        }
        return false;
    }

    public static void setFragmentCallBackA(FragmentCallBackA callBack) {
        fragmentCallBackA = callBack;
    }

    public static void setFragmentCallBackB(FragmentCallBackB callBack) {
        fragmentCallBackB = callBack;
    }
    public static void setFragmentCallBackC(FragmentCallBackC callBack) {
        fragmentCallBackC = callBack;
    }

    public static void setFragmentCallBackD(FragmentCallBackD callBack) {
        fragmentCallBackD = callBack;
    }

    @Override
    public void onOffClick() {
        checkScreenOff(null);

  /*      new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                    checkScreenOn(null);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();*/
    }
    @Override
    public void onSetOnclick() {
        Intent intent = new Intent("/");
        ComponentName cm = new ComponentName("com.android.settings","com.android.settings.Setting");
        intent.setComponent(cm);
        intent.setAction("com.intent.action.VIEW");
        startActivityForResult(intent,0);
    }
    private class MyViewPagerAdapter extends FragmentPagerAdapter {
        public MyViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public android.support.v4.app.Fragment getItem(int i) {
            return frags.get(i);
        }

        @Override
        public int getCount() {
            return frags == null ? 0 : frags.size();
        }
    }

    private void loadFile(String url) {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/download2";
        GetRequest<File> request = OkGo.<File>get(url);
        DownloadTask task = OkDownload.request("taskTag", request)
                .save()
                .folder(path)
                .register(new DownloadListener("taskTag") {
                              @Override
                              public void onStart(Progress progress) {
                                  LogUtil.d("apk", "onStart");
                              }

                              @Override
                              public void onProgress(Progress progress) {

                                  LogUtil.d("apk", "onProgress");
                              }

                              @Override
                              public void onError(Progress progress) {
                                  LogUtil.d("apk", "onError");
                              }

                              @Override
                              public void onFinish(File file, Progress progress) {
                                  LogUtil.d("apk", file.getAbsolutePath());
                                  ApkUtils.install(MainActivity.this, file);
                              }

                              @Override
                              public void onRemove(Progress progress) {
                                  LogUtil.d("apk", "onRemove");
                              }
                          }
                );
        task.restart();//重新下载
    }

    public void checkVersion() {
        versionCodeLocal = Utils.getVersionCode(this);
        LogUtil.d("===", "当前版本："+versionCodeLocal+"开始检查版本更新");
//        OkGo.<String>get("http://192.168.10.120:8080/app/uploadVersionInfo")
        OkGo.<String>get(RequestApi.getUpdataAppUrl())
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        LogUtil.d("===", response.body());
                        try {
                            JSONObject jsonObject = new JSONObject(response.body());
                            versionCodeOnLine = jsonObject.getString("code");
                            appUrl = jsonObject.getString("url");
                            String downUrl = (String) SharedPreferenceTools.getValueofSP(MainActivity.this, "ServiceIp", "");
                            if (downUrl.equals("")) {
                                ToastUtils.showToast(MainActivity.this, " ServcerIp设置有误！");
                                return;
                            } else {
                                appUrl = downUrl + appUrl;
                            }
                            if (versionCodeOnLine != null && appUrl != null) {
                                if (versionCodeOnLine != null) {
                                    if (Integer.parseInt(versionCodeOnLine) > versionCodeLocal) {
                                        ToastUtils.showToast(MainActivity.this, "发现新版本：" + versionCodeOnLine);
                                        ToastUtils.showToast(MainActivity.this, "开始自动更新...");
                                        loadFile(appUrl);
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                    }

                    @Override
                    public void onFinish() {

                    }
                });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.e("====Main", "onDestroy is started");
        stopService(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    long onclickfirst = 0;
    int onclick = 0;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getEventTime() - onclickfirst < 500) {
                onclick++;
                onclickfirst = event.getEventTime();
                if (onclick == 4) {
                    //连续点击5次成功转到设置页面
//                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                    CustomEidtDialog customEidtDialog = new CustomEidtDialog(MainActivity.this, intent);
                    customEidtDialog.show();
                    customEidtDialog.setOnOffListener(this);
                    customEidtDialog.setOnSetListener(this);
                    return false;
                }
            } else {
                onclickfirst = event.getEventTime();
                onclick = 0;
            }
        }
        return true;
    }
    /**
     * 网络类型
     */
    private int netMobile;
    public boolean inspectNet() {
        netMobile = NetUtil.getNetWorkState(MainActivity.this);
        cancleNetTast();
        if(netMobile == 2){
            ToastUtils.showToast(this,"当前网络：以太网");
//            checkVersion();
        }else if (netMobile == 1) {
            ToastUtils.showToast(this,"当前网络：wifi");
//            checkVersion();
        } else if (netMobile == 0) {
            ToastUtils.showToast(this,"当前网络：数据网络");
//            checkVersion();
        } else if (netMobile == -1) {
            checkNetTimer = new Timer();
            checkNetTask = new CheckNetTask();
            checkNetTimer.schedule(checkNetTask,0,periodTime);
        }
        return isNetConnect();
    }
    /**
     * 判断有无网络 。
     *
     * @return true 有网, false 没有网络.
     */
    public boolean isNetConnect() {
        if (netMobile == 1) {
            return true;
        } else if (netMobile == 0) {
            return true;
        } else if (netMobile == 2) {
            return true;
        }else if (netMobile == -1) {
            return false;
        }
        return false;
    }
    @Override
    public void onNetChange(int netMobile) {
        this.netMobile = netMobile;
        cancleNetTast();
        if (netMobile == 1) {
            ToastUtils.showToast(this,"wifi已连接");
//            checkVersion();
        } else if (netMobile == 0) {
            ToastUtils.showToast(this,"数据网络已连接");
//            checkVersion();
        } else if (netMobile == 2) {
            ToastUtils.showToast(this,"以太网络已连接");
//            checkVersion();
        } else if (netMobile == -1) {
            checkNetTimer = new Timer();
            checkNetTask = new CheckNetTask();
            checkNetTimer.schedule(checkNetTask,0,periodTime);
        }
    }

    private void cancleNetTast() {
        if (checkNetTask != null) {
            checkNetTask.cancel();
            checkNetTask = null;
        }
        if (checkNetTimer != null) {
            checkNetTimer.purge();
            checkNetTimer.cancel();
            checkNetTimer = null;
        }
    }

    class CheckNetTask extends TimerTask {
        @Override
        public void run() {
            Message message = new Message();
            message.what = 0x33;
            handler.sendMessage(message);
        }
    }
}
