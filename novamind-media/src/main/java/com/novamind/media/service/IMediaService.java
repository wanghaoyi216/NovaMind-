package com.novamind.media.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.common.domain.dto.PageDTO;
import com.novamind.media.domain.dto.MediaDTO;
import com.novamind.media.domain.dto.MediaUploadResultDTO;
import com.novamind.media.domain.po.Media;
import com.novamind.media.domain.query.MediaQuery;
import com.novamind.media.domain.vo.MediaVO;
import com.novamind.media.domain.vo.VideoPlayVO;

/**
 * <p>
 * 媒资表，主要是视频文件 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-30
 */
public interface IMediaService extends IService<Media> {

    String getUploadSignature();

    VideoPlayVO getPlaySignatureBySectionId(Long fileId);

    MediaDTO save(MediaUploadResultDTO mediaResult);

    void updateMediaProcedureResult(Media media);

    void deleteMedia(String fileId);

    VideoPlayVO getPlaySignatureByMediaId(Long mediaId);

    PageDTO<MediaVO> queryMediaPage(MediaQuery query);
}
