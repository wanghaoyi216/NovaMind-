package com.novamind.aigc.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import com.novamind.aigc.config.AliYunProperties;
import com.novamind.aigc.service.FileStorage;
import com.novamind.common.exceptions.BadRequestException;
import com.novamind.common.exceptions.CommonException;
import com.novamind.common.utils.AssertUtils;
import com.novamind.common.utils.CollUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

import static com.novamind.aigc.constants.FileErrorInfo.Msg.*;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "ALI")
@RequiredArgsConstructor
public class OSSFileStorage implements FileStorage {

    private final OSS ossClient;
    private final AliYunProperties aliYunProperties;

    @Override
    public String uploadFile(String key, InputStream inputStream, long contentLength) {
        var bucketName = this.aliYunProperties.getOss().getBucket();
        AssertUtils.isNotBlank(bucketName, BUCKET_NAME_IS_NULL);
        AssertUtils.isNotBlank(key, FILE_KEY_IS_NULL);
        AssertUtils.isNotNull(inputStream);

        try {
            var objectMeta = new ObjectMetadata();
            objectMeta.setContentLength(contentLength);
            var request = new PutObjectRequest(bucketName, key, inputStream, objectMeta);
            var result = ossClient.putObject(request);
            result.getResponse();
            return StrUtil.format("https://{}.{}/{}", bucketName, aliYunProperties.getOss().getEndpoint(), key);
        } catch (Exception e) {
            log.error("上传文件[{}]失败 ", key, e);
            throw new CommonException("上传文件失败!", e);
        }
    }

    @Override
    public InputStream downloadFile(String key) {
        var bucketName = this.aliYunProperties.getOss().getBucket();
        AssertUtils.isNotBlank(bucketName, BUCKET_NAME_IS_NULL);
        AssertUtils.isNotBlank(key, FILE_KEY_IS_NULL);

        try {
            var request = new GetObjectRequest(bucketName, key);
            return ossClient.getObject(request).getObjectContent();
        } catch (Exception e) {
            log.error("下载文件[{}]时发生异常：", key, e);
            throw new CommonException("文件下载异常。", e);
        }
    }

    @Override
    public void deleteFile(String key) {
        var bucketName = this.aliYunProperties.getOss().getBucket();
        AssertUtils.isNotBlank(bucketName, BUCKET_NAME_IS_NULL);
        AssertUtils.isNotBlank(key, FILE_KEY_IS_NULL);

        try {
            ossClient.deleteObject(bucketName, key);
        } catch (Exception e) {
            log.error("删除文件[{}]时发生异常：", key, e);
            throw new CommonException("删除异常。", e);
        }
    }

    @Override
    public void deleteFiles(List<String> keys) {
        if (CollUtils.isEmpty(keys)) {
            return;
        }

        var bucketName = this.aliYunProperties.getOss().getBucket();
        AssertUtils.isNotBlank(bucketName, BUCKET_NAME_IS_NULL);

        if (keys.size() > 1000) {
            throw new BadRequestException(FILE_KEY_TOO_MANY);
        }

        try {
            var request = new DeleteObjectsRequest(bucketName).withKeys(keys);
            ossClient.deleteObjects(request);
        } catch (Exception e) {
            log.error("批量删除文件[{}]时发生异常：", keys, e);
            throw new CommonException("删除异常。", e);
        }
    }

}
