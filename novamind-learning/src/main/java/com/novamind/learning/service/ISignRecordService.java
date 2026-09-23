package com.novamind.learning.service;

import com.novamind.learning.domain.vo.SignResultVO;

public interface ISignRecordService {
    SignResultVO addSignRecords();

    Byte[] querySignRecords();

}
