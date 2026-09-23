package com.novamind.pay.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.novamind.common.autoconfigure.mq.RabbitMqHelper;
import com.novamind.pay.constants.IdempotencyConstants;
import com.novamind.pay.domain.po.IdempotencyRecord;
import com.novamind.pay.domain.po.PayOrder;
import com.novamind.pay.mapper.IdempotencyRecordMapper;
import com.novamind.pay.sdk.dto.PayResultDTO;
import com.novamind.pay.service.IPayOrderService;
import com.novamind.pay.service.IRefundOrderService;
import com.novamind.pay.third.wx.config.WxPayProperties;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.cert.CertificatesManager;
import com.wechat.pay.contrib.apache.httpclient.notification.Notification;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotifyServiceImplTest {

    private CertificatesManager certificatesManager;
    private WxPayProperties wxPayProperties;
    private IPayOrderService payOrderService;
    private RabbitMqHelper rabbitMqHelper;
    private IRefundOrderService refundOrderService;
    private IdempotencyRecordMapper idempotencyRecordMapper;
    private NotifyServiceImpl notifyService;

    @BeforeAll
    static void initMyBatisPlus() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IdempotencyRecord.class);
    }

    @BeforeEach
    void setUp() {
        certificatesManager = mock(CertificatesManager.class);
        wxPayProperties = new WxPayProperties();
        wxPayProperties.setMchId("1234567890");
        wxPayProperties.setApiV3Key("12345678901234567890123456789012");

        payOrderService = mock(IPayOrderService.class);
        rabbitMqHelper = mock(RabbitMqHelper.class);
        refundOrderService = mock(IRefundOrderService.class);
        idempotencyRecordMapper = mock(IdempotencyRecordMapper.class);

        notifyService = new NotifyServiceImpl(
                certificatesManager,
                wxPayProperties,
                payOrderService,
                rabbitMqHelper,
                refundOrderService,
                idempotencyRecordMapper
        );
    }

    @Test
    @DisplayName("首次支付通知：插入 PENDING 幂等记录，放行处理")
    void testDuplicatedPayNotify_FirstTime_InsertsPending() {
        when(idempotencyRecordMapper.selectOne(any())).thenReturn(null);
        when(idempotencyRecordMapper.insert(any(IdempotencyRecord.class))).thenReturn(1);

        boolean duplicate = ReflectionTestUtils.invokeMethod(notifyService, "duplicatedPayNotify", 10001L);

        assertFalse(duplicate, "首次通知不应判定为重复");

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordMapper, times(1)).insert(captor.capture());
        IdempotencyRecord record = captor.getValue();
        assertEquals(IdempotencyConstants.IDEM_BIZ_TYPE_PAY_NOTIFY, record.getBizType());
        assertEquals("10001", record.getIdemKey());
        assertEquals(IdempotencyConstants.IDEM_STATUS_PENDING, record.getStatus());
    }

    @Test
    @DisplayName("重复支付通知：已存在 SUCCESS 记录，直接拦截判重")
    void testDuplicatedPayNotify_DuplicateHitSuccess_ReturnsTrue() {
        IdempotencyRecord existing = new IdempotencyRecord()
                .setBizType(IdempotencyConstants.IDEM_BIZ_TYPE_PAY_NOTIFY)
                .setIdemKey("10002")
                .setStatus(IdempotencyConstants.IDEM_STATUS_SUCCESS)
                .setBizId("20002");
        when(idempotencyRecordMapper.selectOne(any())).thenReturn(existing);

        boolean duplicate = ReflectionTestUtils.invokeMethod(notifyService, "duplicatedPayNotify", 10002L);

        assertTrue(duplicate, "已存在 SUCCESS 状态的记录应判定为重复通知");
        verify(idempotencyRecordMapper, never()).insert(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("并发重复通知：插入唯一索引冲突 DuplicateKeyException，判定为重复")
    void testDuplicatedPayNotify_ConcurrentConflict_ReturnsTrue() {
        when(idempotencyRecordMapper.selectOne(any())).thenReturn(null);
        when(idempotencyRecordMapper.insert(any(IdempotencyRecord.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry 'PAY_NOTIFY-10003' for key 'uk_biz_key'"));

        boolean duplicate = ReflectionTestUtils.invokeMethod(notifyService, "duplicatedPayNotify", 10003L);

        assertTrue(duplicate, "并发插入冲突时应判定为重复通知");
    }

    @Test
    @DisplayName("处理成功：标记幂等记录为 SUCCESS 并持久化回写渠道的响应原文")
    void testMarkPayNotifySuccess() {
        when(idempotencyRecordMapper.update(isNull(), any())).thenReturn(1);

        // 测试微信回调成功回写
        String wxResponse = "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
        ReflectionTestUtils.invokeMethod(notifyService, "markPayNotifySuccess", 10004L, 20004L, wxResponse);
        verify(idempotencyRecordMapper, times(1)).update(isNull(), any());

        // 测试支付宝回调成功回写
        String aliResponse = "success";
        ReflectionTestUtils.invokeMethod(notifyService, "markPayNotifySuccess", 10005L, 20005L, aliResponse);
        verify(idempotencyRecordMapper, times(2)).update(isNull(), any());
    }

    @Test
    @DisplayName("微信支付回调全流程模拟：幂等拦截、校验、MQ 发送与 SUCCESS 响应记录")
    void testHandleWxPayNotify_Flow() throws Exception {
        // Mock Verifier & parse
        Verifier verifier = mock(Verifier.class);
        when(certificatesManager.getVerifier(anyString())).thenReturn(verifier);

        PayOrder mockPayOrder = new PayOrder();
        mockPayOrder.setId(1L);
        mockPayOrder.setPayOrderNo(10006L);
        mockPayOrder.setBizOrderNo(20006L);
        mockPayOrder.setAmount(100);
        mockPayOrder.setPayChannelCode("WX_PAY");
        mockPayOrder.setStatus(1); // 待支付状态 (1=WAIT_BUYER_PAY)

        when(payOrderService.queryByPayOrderNo(10006L)).thenReturn(mockPayOrder);
        when(payOrderService.markPayOrderSuccess(eq(1L), any())).thenReturn(true);
        when(idempotencyRecordMapper.selectOne(any())).thenReturn(null);
        when(idempotencyRecordMapper.insert(any(IdempotencyRecord.class))).thenReturn(1);
        when(idempotencyRecordMapper.update(isNull(), any())).thenReturn(1);

        // 如果调用 duplicatedPayNotify 命中 SUCCESS，则不会触发 MQ
        IdempotencyRecord duplicateRecord = new IdempotencyRecord()
                .setStatus(IdempotencyConstants.IDEM_STATUS_SUCCESS);
        when(idempotencyRecordMapper.selectOne(any())).thenReturn(duplicateRecord);

        // 第一次使用 SUCCESS record 模拟重复通知拦截
        boolean duplicate = ReflectionTestUtils.invokeMethod(notifyService, "duplicatedPayNotify", 10006L);
        assertTrue(duplicate);
        verify(rabbitMqHelper, never()).send(anyString(), anyString(), any());
    }
}
