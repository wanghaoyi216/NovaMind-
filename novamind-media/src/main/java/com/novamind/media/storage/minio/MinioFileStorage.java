package com.novamind.media.storage.minio;

import com.novamind.common.exceptions.BadRequestException;
import com.novamind.common.exceptions.CommonException;
import com.novamind.common.utils.AssertUtils;
import com.novamind.common.utils.CollUtils;
import com.novamind.media.config.MinioProperties;
import com.novamind.media.storage.IFileStorage;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.messages.DeleteObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;

import static com.novamind.media.enums.FileErrorInfo.Msg.*;

@Slf4j
@RequiredArgsConstructor
public class MinioFileStorage implements IFileStorage {
    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public String uploadFile(String key, InputStream inputStream, long contentLength) {
        AssertUtils.isNotBlank(properties.getBucket(), BUCKET_NAME_IS_NULL);
        AssertUtils.isNotBlank(key, FILE_KEY_IS_NULL);
        AssertUtils.isNotNull(inputStream);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .stream(inputStream, contentLength, -1)
                    .build());
            return key;
        } catch (Exception e) {
            log.error("上传文件[{}]失败", key, e);
            throw new CommonException("上传文件失败!", e);
        }
    }

    @Override
    public InputStream downloadFile(String key) {
        AssertUtils.isNotBlank(properties.getBucket(), BUCKET_NAME_IS_NULL);
        AssertUtils.isNotBlank(key, FILE_KEY_IS_NULL);
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            log.error("下载文件[{}]时发生异常：", key, e);
            throw new CommonException("文件下载异常。", e);
        }
    }

    @Override
    public void deleteFile(String key) {
        AssertUtils.isNotBlank(properties.getBucket(), BUCKET_NAME_IS_NULL);
        AssertUtils.isNotBlank(key, FILE_KEY_IS_NULL);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
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
        AssertUtils.isNotBlank(properties.getBucket(), BUCKET_NAME_IS_NULL);
        if (keys.size() > 1000) {
            throw new BadRequestException(FILE_KEY_TOO_MANY);
        }
        try {
            List<DeleteObject> objects = keys.stream().map(DeleteObject::new).toList();
            minioClient.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(properties.getBucket())
                    .objects(objects)
                    .build());
        } catch (Exception e) {
            log.error("批量删除文件[{}]时发生异常：", keys, e);
            throw new CommonException("删除异常。", e);
        }
    }
}

