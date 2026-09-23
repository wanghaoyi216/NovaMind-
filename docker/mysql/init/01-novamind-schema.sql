-- Generated from MyBatis Plus entity classes for local Docker deployment.
-- This bootstrap schema is intentionally permissive: it restores table availability, not production indexes or seed data.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `novamind_aigc` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_auth` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_course` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_data` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_exam` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_learning` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_media` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_message` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_pay` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_promotion` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_remark` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_search` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_trade` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `novamind_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `xxl_job` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `novamind_aigc`;

-- novamind-aigc\src\main\java\com\novamind\aigc\entity\ChatRecord.java
CREATE TABLE IF NOT EXISTS `chat_record` (
  `id` bigint NOT NULL,
  `conversation_id` varchar(255) NULL,
  `data` text NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-aigc\src\main\java\com\novamind\aigc\entity\ChatSession.java
CREATE TABLE IF NOT EXISTS `chat_session` (
  `id` bigint NOT NULL,
  `session_id` varchar(255) NULL,
  `user_id` bigint NULL,
  `title` varchar(255) NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_auth`;

-- novamind-auth\novamind-auth-service\src\main\java\com\novamind\auth\domain\po\Privilege.java
CREATE TABLE IF NOT EXISTS `privilege` (
  `id` bigint NOT NULL,
  `menu_id` bigint NULL,
  `intro` text NULL,
  `method` varchar(255) NULL,
  `uri` varchar(255) NULL,
  `internal` tinyint(1) NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `dep_id` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-auth\novamind-auth-service\src\main\java\com\novamind\auth\domain\po\AccountRole.java
CREATE TABLE IF NOT EXISTS `account_role` (
  `id` bigint NOT NULL,
  `account_id` bigint NULL,
  `role_id` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-auth\novamind-auth-service\src\main\java\com\novamind\auth\domain\po\LoginRecord.java
CREATE TABLE IF NOT EXISTS `login_record` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL,
  `cell_phone` varchar(255) NULL,
  `login_time` datetime NULL,
  `logout_time` datetime NULL,
  `login_date` date NULL,
  `duration` bigint NULL,
  `ipv4` varchar(255) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-auth\novamind-auth-service\src\main\java\com\novamind\auth\domain\po\Menu.java
CREATE TABLE IF NOT EXISTS `menu` (
  `id` bigint NOT NULL,
  `parent_id` bigint NULL,
  `has_children` tinyint(1) NULL,
  `label` varchar(255) NULL,
  `path` varchar(255) NULL,
  `icon` varchar(255) NULL,
  `priority` int NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `dep_id` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-auth\novamind-auth-service\src\main\java\com\novamind\auth\domain\po\Role.java
CREATE TABLE IF NOT EXISTS `role` (
  `id` bigint NOT NULL,
  `code` varchar(255) NULL,
  `name` varchar(255) NULL,
  `type` int NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `dep_id` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-auth\novamind-auth-service\src\main\java\com\novamind\auth\domain\po\RoleMenu.java
CREATE TABLE IF NOT EXISTS `role_menu` (
  `id` bigint NOT NULL,
  `role_id` bigint NULL,
  `menu_id` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-auth\novamind-auth-service\src\main\java\com\novamind\auth\domain\po\RolePrivilege.java
CREATE TABLE IF NOT EXISTS `role_privilege` (
  `id` bigint NOT NULL,
  `role_id` bigint NULL,
  `privilege_id` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_course`;

-- novamind-course\src\main\java\com\novamind\course\domain\po\Category.java
CREATE TABLE IF NOT EXISTS `category` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `parent_id` bigint NULL,
  `level` int NULL,
  `priority` int NULL,
  `status` int NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\Course.java
CREATE TABLE IF NOT EXISTS `course` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `course_type` int NULL,
  `cover_url` varchar(255) NULL,
  `first_cate_id` bigint NULL,
  `second_cate_id` bigint NULL,
  `third_cate_id` bigint NULL,
  `free` int NULL,
  `price` int NULL,
  `template_type` int NULL,
  `template_url` text NULL,
  `status` int NULL,
  `purchase_start_time` datetime NULL,
  `purchase_end_time` datetime NULL,
  `step` int NULL,
  `score` int NULL,
  `media_duration` int NULL,
  `valid_duration` int NULL,
  `section_num` int NULL,
  `dep_id` bigint NULL,
  `publish_times` int NULL,
  `publish_time` datetime NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseCataSubject.java
CREATE TABLE IF NOT EXISTS `course_cata_subject` (
  `id` bigint NOT NULL,
  `course_id` bigint NULL,
  `cata_id` bigint NULL,
  `subject_id` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseCataSubjectDraft.java
CREATE TABLE IF NOT EXISTS `course_cata_subject_draft` (
  `id` bigint NOT NULL,
  `course_id` bigint NULL,
  `cata_id` bigint NULL,
  `subject_id` bigint NULL,
  `create_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseCatalogue.java
CREATE TABLE IF NOT EXISTS `course_catalogue` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `trailer` int NULL,
  `course_id` bigint NULL,
  `type` int NULL,
  `parent_catalogue_id` bigint NULL,
  `media_id` bigint NULL,
  `video_id` bigint NULL,
  `video_name` varchar(255) NULL,
  `living_start_time` datetime NULL,
  `living_end_time` datetime NULL,
  `play_back` int NULL,
  `media_duration` int NULL,
  `c_index` int NULL,
  `dep_id` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseCatalogueDraft.java
CREATE TABLE IF NOT EXISTS `course_catalogue_draft` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `trailer` int NULL,
  `course_id` bigint NULL,
  `type` int NULL,
  `parent_catalogue_id` bigint NULL,
  `media_id` bigint NULL,
  `video_id` bigint NULL,
  `video_name` varchar(255) NULL,
  `living_start_time` datetime NULL,
  `living_end_time` datetime NULL,
  `play_back` int NULL,
  `c_index` int NULL,
  `media_duration` int NULL,
  `can_update` tinyint(1) NULL,
  `dep_id` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseContent.java
CREATE TABLE IF NOT EXISTS `course_content` (
  `id` bigint NOT NULL,
  `course_introduce` text NULL,
  `use_people` varchar(255) NULL,
  `course_detail` text NULL,
  `dep_id` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseContentDraft.java
CREATE TABLE IF NOT EXISTS `course_content_draft` (
  `id` bigint NOT NULL,
  `course_introduce` text NULL,
  `use_people` varchar(255) NULL,
  `course_detail` text NULL,
  `dep_id` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseDraft.java
CREATE TABLE IF NOT EXISTS `course_draft` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `course_type` int NULL,
  `cover_url` varchar(255) NULL,
  `first_cate_id` bigint NULL,
  `second_cate_id` bigint NULL,
  `third_cate_id` bigint NULL,
  `free` int NULL,
  `price` int NULL,
  `template_type` int NULL,
  `template_url` text NULL,
  `status` int NULL,
  `purchase_start_time` datetime NULL,
  `purchase_end_time` datetime NULL,
  `step` int NULL,
  `score` int NULL,
  `media_duration` int NULL,
  `valid_duration` int NULL,
  `section_num` int NULL,
  `can_update` tinyint(1) NULL,
  `c_version` int NULL,
  `dep_id` bigint NULL,
  `publish_time` datetime NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseSubject.java
CREATE TABLE IF NOT EXISTS `course_subject` (
  `id` bigint NOT NULL,
  `course_id` bigint NULL,
  `subject_id` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseTeacher.java
CREATE TABLE IF NOT EXISTS `course_teacher` (
  `id` bigint NOT NULL,
  `course_id` bigint NULL,
  `teacher_id` bigint NULL,
  `is_show` int NULL,
  `c_index` int NULL,
  `dep_id` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\CourseTeacherDraft.java
CREATE TABLE IF NOT EXISTS `course_teacher_draft` (
  `id` bigint NOT NULL,
  `course_id` bigint NULL,
  `teacher_id` bigint NULL,
  `is_show` int NULL,
  `c_index` int NULL,
  `dep_id` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\Subject.java
CREATE TABLE IF NOT EXISTS `subject` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `subject_type` int NULL,
  `difficulty` int NULL,
  `option1` varchar(255) NULL,
  `option2` varchar(255) NULL,
  `option3` varchar(255) NULL,
  `option4` varchar(255) NULL,
  `option5` varchar(255) NULL,
  `option6` varchar(255) NULL,
  `option7` varchar(255) NULL,
  `option8` varchar(255) NULL,
  `option9` varchar(255) NULL,
  `option10` varchar(255) NULL,
  `answer` varchar(255) NULL,
  `analysis` varchar(255) NULL,
  `correct_times` int NULL,
  `score` int NULL,
  `dep_id` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `use_times` int NULL,
  `answer_times` int NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-course\src\main\java\com\novamind\course\domain\po\SubjectCategory.java
CREATE TABLE IF NOT EXISTS `subject_category` (
  `id` bigint NOT NULL,
  `subject_id` bigint NULL,
  `first_cate_id` bigint NULL,
  `second_cate_id` bigint NULL,
  `third_cate_id` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_exam`;

-- novamind-exam\src\main\java\com\novamind\exam\domain\po\Question.java
CREATE TABLE IF NOT EXISTS `question` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `type` int NULL,
  `cate_id1` bigint NULL,
  `cate_id2` bigint NULL,
  `cate_id3` bigint NULL,
  `difficulty` int NULL,
  `correct_times` int NULL,
  `answer_times` int NULL,
  `score` int NULL,
  `dep_id` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-exam\src\main\java\com\novamind\exam\domain\po\QuestionBiz.java
CREATE TABLE IF NOT EXISTS `question_biz` (
  `id` bigint NOT NULL,
  `biz_id` bigint NULL,
  `question_id` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-exam\src\main\java\com\novamind\exam\domain\po\QuestionDetail.java
CREATE TABLE IF NOT EXISTS `question_detail` (
  `id` bigint NOT NULL,
  `options` json NULL,
  `answer` varchar(255) NULL,
  `analysis` varchar(255) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_learning`;

-- novamind-learning\src\main\java\com\novamind\learning\domain\po\InteractionQuestion.java
CREATE TABLE IF NOT EXISTS `interaction_question` (
  `id` bigint NOT NULL,
  `title` varchar(255) NULL,
  `description` text NULL,
  `course_id` bigint NULL,
  `chapter_id` bigint NULL,
  `section_id` bigint NULL,
  `user_id` bigint NULL,
  `latest_answer_id` bigint NULL,
  `answer_times` int NULL,
  `anonymity` tinyint(1) NULL,
  `hidden` tinyint(1) NULL,
  `status` int NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-learning\src\main\java\com\novamind\learning\domain\po\InteractionReply.java
CREATE TABLE IF NOT EXISTS `interaction_reply` (
  `id` bigint NOT NULL,
  `question_id` bigint NULL,
  `answer_id` bigint NULL,
  `user_id` bigint NULL,
  `content` text NULL,
  `target_user_id` bigint NULL,
  `target_reply_id` bigint NULL,
  `reply_times` int NULL,
  `liked_times` int NULL,
  `hidden` tinyint(1) NULL,
  `anonymity` tinyint(1) NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-learning\src\main\java\com\novamind\learning\domain\po\LearningLesson.java
CREATE TABLE IF NOT EXISTS `learning_lesson` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL,
  `course_id` bigint NULL,
  `status` int NULL,
  `week_freq` int NULL,
  `plan_status` int NULL,
  `learned_sections` int NULL,
  `latest_section_id` bigint NULL,
  `latest_learn_time` datetime NULL,
  `create_time` datetime NULL,
  `expire_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-learning\src\main\java\com\novamind\learning\domain\po\LearningRecord.java
CREATE TABLE IF NOT EXISTS `learning_record` (
  `id` bigint NOT NULL,
  `lesson_id` bigint NULL,
  `section_id` bigint NULL,
  `user_id` bigint NULL,
  `moment` int NULL,
  `finished` tinyint(1) NULL,
  `create_time` datetime NULL,
  `finish_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-learning\src\main\java\com\novamind\learning\domain\po\Note.java
CREATE TABLE IF NOT EXISTS `note` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL,
  `course_id` bigint NULL,
  `chapter_id` bigint NULL,
  `section_id` bigint NULL,
  `note_moment` int NULL,
  `content` text NULL,
  `is_private` tinyint(1) NULL,
  `hidden` tinyint(1) NULL,
  `hidden_reason` text NULL,
  `author_id` bigint NULL,
  `gathered_note_id` bigint NULL,
  `is_gathered` tinyint(1) NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-learning\src\main\java\com\novamind\learning\domain\po\NoteUser.java
CREATE TABLE IF NOT EXISTS `note_user` (
  `id` bigint NOT NULL,
  `note_id` bigint NULL,
  `user_id` bigint NULL,
  `is_gathered` tinyint(1) NULL,
  `create_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-learning\src\main\java\com\novamind\learning\domain\po\PointsBoard.java
CREATE TABLE IF NOT EXISTS `points_board` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL,
  `points` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-learning\src\main\java\com\novamind\learning\domain\po\PointsBoardSeason.java
CREATE TABLE IF NOT EXISTS `points_board_season` (
  `id` int NOT NULL,
  `name` varchar(255) NULL,
  `begin_time` date NULL,
  `end_time` date NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-learning\src\main\java\com\novamind\learning\domain\po\PointsRecord.java
CREATE TABLE IF NOT EXISTS `points_record` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL,
  `type` int NULL,
  `points` int NULL,
  `create_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_media`;

-- novamind-media\src\main\java\com\novamind\media\domain\po\File.java
CREATE TABLE IF NOT EXISTS `file` (
  `id` bigint NOT NULL,
  `key` varchar(255) NULL,
  `filename` varchar(255) NULL,
  `request_id` varchar(255) NULL,
  `status` int NULL,
  `platform` int NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-media\src\main\java\com\novamind\media\domain\po\Media.java
CREATE TABLE IF NOT EXISTS `media` (
  `id` bigint NOT NULL,
  `file_id` varchar(255) NULL,
  `filename` varchar(255) NULL,
  `media_url` varchar(255) NULL,
  `cover_url` varchar(255) NULL,
  `duration` double NULL,
  `request_id` varchar(255) NULL,
  `status` int NULL,
  `size` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_message`;

-- novamind-message\novamind-message-service\src\main\java\com\novamind\message\domain\po\MessageTemplate.java
CREATE TABLE IF NOT EXISTS `message_template` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `platform_code` varchar(255) NULL,
  `sign_name` varchar(255) NULL,
  `third_template_code` text NULL,
  `content` text NULL,
  `template_id` bigint NULL,
  `status` int NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-message\novamind-message-service\src\main\java\com\novamind\message\domain\po\NoticeTask.java
CREATE TABLE IF NOT EXISTS `notice_task` (
  `id` bigint NOT NULL,
  `template_id` bigint NULL,
  `name` varchar(255) NULL,
  `partial` tinyint(1) NULL,
  `push_time` datetime NULL,
  `max_times` int NULL,
  `interval` int NULL,
  `expire_time` datetime NULL,
  `finished` tinyint(1) NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-message\novamind-message-service\src\main\java\com\novamind\message\domain\po\NoticeTemplate.java
CREATE TABLE IF NOT EXISTS `notice_template` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `code` varchar(255) NULL,
  `type` int NULL,
  `status` int NULL,
  `title` varchar(255) NULL,
  `content` text NULL,
  `is_sms_template` tinyint(1) NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-message\novamind-message-service\src\main\java\com\novamind\message\domain\po\PublicNotice.java
CREATE TABLE IF NOT EXISTS `public_notice` (
  `id` bigint NOT NULL,
  `type` int NULL,
  `title` varchar(255) NULL,
  `content` text NULL,
  `push_time` datetime NULL,
  `create_time` datetime NULL,
  `expire_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-message\novamind-message-service\src\main\java\com\novamind\message\domain\po\SmsThirdPlatform.java
CREATE TABLE IF NOT EXISTS `sms_third_platform` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `code` varchar(255) NULL,
  `priority` int NULL,
  `status` int NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-message\novamind-message-service\src\main\java\com\novamind\message\domain\po\UserInbox.java
CREATE TABLE IF NOT EXISTS `user_inbox` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL,
  `type` int NULL,
  `title` varchar(255) NULL,
  `content` text NULL,
  `is_read` tinyint(1) NULL,
  `publisher` bigint NULL,
  `push_time` datetime NULL,
  `expire_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_pay`;

-- novamind-pay\novamind-pay-service\src\main\java\com\novamind\pay\domain\po\PayChannel.java
CREATE TABLE IF NOT EXISTS `pay_channel` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `channel_code` varchar(255) NULL,
  `channel_priority` int NULL,
  `channel_icon` varchar(255) NULL,
  `status` int NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-pay\novamind-pay-service\src\main\java\com\novamind\pay\domain\po\PayOrder.java
CREATE TABLE IF NOT EXISTS `pay_order` (
  `id` bigint NOT NULL,
  `biz_order_no` bigint NULL,
  `pay_order_no` bigint NULL,
  `biz_user_id` bigint NULL,
  `pay_channel_code` varchar(255) NULL,
  `amount` int NULL,
  `pay_type` int NULL,
  `status` int NULL,
  `expand_json` varchar(255) NULL,
  `notify_url` varchar(255) NULL,
  `notify_times` int NULL,
  `notify_status` int NULL,
  `result_code` varchar(255) NULL,
  `result_msg` varchar(255) NULL,
  `pay_success_time` datetime NULL,
  `pay_over_time` datetime NULL,
  `qr_code_url` varchar(255) NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` tinyint(1) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-pay\novamind-pay-service\src\main\java\com\novamind\pay\domain\po\RefundOrder.java
CREATE TABLE IF NOT EXISTS `refund_order` (
  `id` bigint NOT NULL,
  `biz_order_no` bigint NULL,
  `biz_refund_order_no` bigint NULL,
  `pay_order_no` bigint NULL,
  `refund_order_no` bigint NULL,
  `refund_amount` int NULL,
  `total_amount` int NULL,
  `is_split` tinyint(1) NULL,
  `pay_channel_code` varchar(255) NULL,
  `result_code` varchar(255) NULL,
  `result_msg` varchar(255) NULL,
  `status` int NULL,
  `refund_channel` varchar(255) NULL,
  `notify_failed_times` int NULL,
  `notify_status` int NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` tinyint(1) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_promotion`;

-- novamind-promotion\src\main\java\com\novamind\promotion\domain\po\Coupon.java
CREATE TABLE IF NOT EXISTS `coupon` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `type` int NULL,
  `discount_type` int NULL,
  `specific` tinyint(1) NULL,
  `discount_value` int NULL,
  `threshold_amount` int NULL,
  `max_discount_amount` int NULL,
  `obtain_way` int NULL,
  `issue_begin_time` datetime NULL,
  `issue_end_time` datetime NULL,
  `term_days` int NULL,
  `term_begin_time` datetime NULL,
  `term_end_time` datetime NULL,
  `status` int NULL,
  `total_num` int NULL,
  `issue_num` int NULL,
  `used_num` int NULL,
  `user_limit` int NULL,
  `ext_param` text NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-promotion\src\main\java\com\novamind\promotion\domain\po\CouponScope.java
CREATE TABLE IF NOT EXISTS `coupon_scope` (
  `id` bigint NOT NULL,
  `type` int NULL,
  `coupon_id` bigint NULL,
  `biz_id` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-promotion\src\main\java\com\novamind\promotion\domain\po\ExchangeCode.java
CREATE TABLE IF NOT EXISTS `exchange_code` (
  `id` int NOT NULL,
  `code` varchar(255) NULL,
  `status` int NULL,
  `user_id` bigint NULL,
  `type` int NULL,
  `exchange_target_id` bigint NULL,
  `create_time` datetime NULL,
  `expired_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-promotion\src\main\java\com\novamind\promotion\domain\po\Promotion.java
CREATE TABLE IF NOT EXISTS `promotion` (
  `id` bigint NOT NULL,
  `name` varchar(255) NULL,
  `type` int NULL,
  `hot` int NULL,
  `begin_time` datetime NULL,
  `end_time` datetime NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-promotion\src\main\java\com\novamind\promotion\domain\po\UserCoupon.java
CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL,
  `coupon_id` bigint NULL,
  `term_begin_time` datetime NULL,
  `term_end_time` datetime NULL,
  `used_time` datetime NULL,
  `status` int NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_remark`;

-- novamind-remark\src\main\java\com\novamind\remark\domain\po\LikedRecord.java
CREATE TABLE IF NOT EXISTS `liked_record` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL,
  `biz_id` bigint NULL,
  `biz_type` varchar(255) NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_search`;

-- novamind-search\src\main\java\com\novamind\search\domain\po\Interests.java
CREATE TABLE IF NOT EXISTS `interests` (
  `id` bigint NOT NULL,
  `interests` varchar(255) NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_trade`;

-- novamind-trade\src\main\java\com\novamind\trade\domain\po\Order.java
CREATE TABLE IF NOT EXISTS `order` (
  `id` bigint NOT NULL,
  `pay_order_no` bigint NULL,
  `user_id` bigint NULL,
  `status` int NULL,
  `message` text NULL,
  `total_amount` int NULL,
  `real_amount` int NULL,
  `discount_amount` int NULL,
  `pay_channel` varchar(255) NULL,
  `coupon_ids` json NULL,
  `create_time` datetime NULL,
  `pay_time` datetime NULL,
  `close_time` datetime NULL,
  `finish_time` datetime NULL,
  `refund_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `deleted` int NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-trade\src\main\java\com\novamind\trade\domain\po\Cart.java
CREATE TABLE IF NOT EXISTS `cart` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL,
  `course_id` bigint NULL,
  `cover_url` varchar(255) NULL,
  `course_name` varchar(255) NULL,
  `price` int NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-trade\src\main\java\com\novamind\trade\domain\po\OrderDetail.java
CREATE TABLE IF NOT EXISTS `order_detail` (
  `id` bigint NOT NULL,
  `order_id` bigint NULL,
  `user_id` bigint NULL,
  `course_id` bigint NULL,
  `price` int NULL,
  `name` varchar(255) NULL,
  `cover_url` varchar(255) NULL,
  `valid_duration` int NULL,
  `course_expire_time` datetime NULL,
  `discount_amount` int NULL,
  `real_pay_amount` int NULL,
  `status` int NULL,
  `refund_status` int NULL,
  `pay_channel` varchar(255) NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-trade\src\main\java\com\novamind\trade\domain\po\RefundApply.java
CREATE TABLE IF NOT EXISTS `refund_apply` (
  `id` bigint NOT NULL,
  `order_detail_id` bigint NULL,
  `order_id` bigint NULL,
  `refund_order_no` bigint NULL,
  `user_id` bigint NULL,
  `refund_amount` int NULL,
  `status` int NULL,
  `message` text NULL,
  `refund_reason` text NULL,
  `question_desc` text NULL,
  `approver` bigint NULL,
  `approve_opinion` text NULL,
  `remark` varchar(255) NULL,
  `refund_channel` varchar(255) NULL,
  `failed_reason` text NULL,
  `create_time` datetime NULL,
  `approve_time` datetime NULL,
  `finish_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_user`;

-- novamind-user\src\main\java\com\novamind\user\domain\po\User.java
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL,
  `username` varchar(255) NULL,
  `cell_phone` varchar(255) NULL,
  `password` varchar(255) NULL,
  `status` int NULL,
  `type` int NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- novamind-user\src\main\java\com\novamind\user\domain\po\UserDetail.java
CREATE TABLE IF NOT EXISTS `user_detail` (
  `id` bigint NOT NULL,
  `type` int NULL,
  `name` varchar(255) NULL,
  `gender` int NULL,
  `icon` varchar(255) NULL,
  `email` varchar(255) NULL,
  `qq` varchar(255) NULL,
  `birthday` date NULL,
  `job` varchar(255) NULL,
  `province` varchar(255) NULL,
  `city` varchar(255) NULL,
  `district` varchar(255) NULL,
  `intro` text NULL,
  `photo` text NULL,
  `role_id` bigint NULL,
  `create_time` datetime NULL,
  `update_time` datetime NULL,
  `creater` bigint NULL,
  `updater` bigint NULL,
  `dep_id` bigint NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_aigc`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_auth`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_course`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_data`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_exam`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_learning`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_media`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_message`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_pay`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_promotion`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_remark`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_search`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_trade`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `novamind_user`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE `xxl_job`;
CREATE TABLE IF NOT EXISTS `xxl_job_info` (
  `id` int NOT NULL AUTO_INCREMENT,
  `job_group` int NOT NULL,
  `job_desc` varchar(255) NOT NULL,
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `author` varchar(64) DEFAULT NULL,
  `alarm_email` varchar(255) DEFAULT NULL,
  `schedule_type` varchar(50) NOT NULL DEFAULT 'NONE',
  `schedule_conf` varchar(128) DEFAULT NULL,
  `misfire_strategy` varchar(50) NOT NULL DEFAULT 'DO_NOTHING',
  `executor_route_strategy` varchar(50) DEFAULT NULL,
  `executor_handler` varchar(255) DEFAULT NULL,
  `executor_param` varchar(512) DEFAULT NULL,
  `executor_block_strategy` varchar(50) DEFAULT NULL,
  `executor_timeout` int NOT NULL DEFAULT 0,
  `executor_fail_retry_count` int NOT NULL DEFAULT 0,
  `glue_type` varchar(50) NOT NULL,
  `glue_source` mediumtext,
  `glue_remark` varchar(128) DEFAULT NULL,
  `glue_updatetime` datetime DEFAULT NULL,
  `child_jobid` varchar(255) DEFAULT NULL,
  `trigger_status` tinyint NOT NULL DEFAULT 0,
  `trigger_last_time` bigint NOT NULL DEFAULT 0,
  `trigger_next_time` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;
