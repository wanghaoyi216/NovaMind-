package com.novamind.learning.service;

import com.novamind.common.domain.dto.PageDTO;
import com.novamind.learning.domain.dto.NoteFormDTO;
import com.novamind.learning.domain.po.Note;
import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.learning.domain.query.NoteAdminPageQuery;
import com.novamind.learning.domain.query.NotePageQuery;
import com.novamind.learning.domain.vo.NoteAdminDetailVO;
import com.novamind.learning.domain.vo.NoteAdminVO;
import com.novamind.learning.domain.vo.NoteVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 */
public interface INoteService extends IService<Note> {

    void saveNote(NoteFormDTO noteDTO);

    void gatherNote(Long id);

    void removeGatherNote(Long id);

    void updateNote(NoteFormDTO noteDTO);

    PageDTO<NoteVO> queryNotePage(NotePageQuery query);

    PageDTO<NoteAdminVO> queryNotePageForAdmin(NoteAdminPageQuery query);

    NoteAdminDetailVO queryNoteDetailForAdmin(Long id);

    void hiddenNote(Long id, boolean hidden);

    void removeMyNote(Long id);
}
