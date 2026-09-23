SET NAMES utf8mb4;

USE `novamind_course`;

INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `priority`, `status`, `create_time`, `update_time`, `creater`, `updater`, `deleted`)
SELECT 1000001, '后端开发', 0, 1, 1, 1, NOW(), NOW(), 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `id` = 1000001);

INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `priority`, `status`, `create_time`, `update_time`, `creater`, `updater`, `deleted`)
SELECT 1000002, 'Java', 1000001, 2, 1, 1, NOW(), NOW(), 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `id` = 1000002);

INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `priority`, `status`, `create_time`, `update_time`, `creater`, `updater`, `deleted`)
SELECT 1000003, 'Spring Boot', 1000002, 3, 1, 1, NOW(), NOW(), 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `id` = 1000003);

INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `priority`, `status`, `create_time`, `update_time`, `creater`, `updater`, `deleted`)
SELECT 1000004, '前端开发', 0, 1, 2, 1, NOW(), NOW(), 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `id` = 1000004);

INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `priority`, `status`, `create_time`, `update_time`, `creater`, `updater`, `deleted`)
SELECT 1000005, 'Vue', 1000004, 2, 1, 1, NOW(), NOW(), 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `id` = 1000005);

INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `priority`, `status`, `create_time`, `update_time`, `creater`, `updater`, `deleted`)
SELECT 1000006, '工程化', 1000005, 3, 1, 1, NOW(), NOW(), 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `id` = 1000006);

INSERT INTO `course` (`id`, `name`, `course_type`, `cover_url`, `first_cate_id`, `second_cate_id`, `third_cate_id`, `free`, `price`, `template_type`, `template_url`, `status`, `purchase_start_time`, `purchase_end_time`, `step`, `score`, `media_duration`, `valid_duration`, `section_num`, `dep_id`, `publish_times`, `publish_time`, `create_time`, `update_time`, `creater`, `updater`, `deleted`)
SELECT 2000001, 'Spring Boot 企业级实战', 1, 'http://localhost:9000/novamind-assets/course-covers/course_spring_boot_01.jpg', 1000001, 1000002, 1000003, 0, 19900, 1, NULL, 2, NOW(), DATE_ADD(NOW(), INTERVAL 2 YEAR), 5, 95, 3600, 365, 18, 1, 1, NOW(), NOW(), NOW(), 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `course` WHERE `id` = 2000001);

INSERT INTO `course` (`id`, `name`, `course_type`, `cover_url`, `first_cate_id`, `second_cate_id`, `third_cate_id`, `free`, `price`, `template_type`, `template_url`, `status`, `purchase_start_time`, `purchase_end_time`, `step`, `score`, `media_duration`, `valid_duration`, `section_num`, `dep_id`, `publish_times`, `publish_time`, `create_time`, `update_time`, `creater`, `updater`, `deleted`)
SELECT 2000002, 'Vue3 前端工程化训练营', 1, 'http://localhost:9000/novamind-assets/course-covers/course_ui_ux_design_01.jpg', 1000004, 1000005, 1000006, 0, 16900, 1, NULL, 2, NOW(), DATE_ADD(NOW(), INTERVAL 2 YEAR), 5, 92, 3000, 365, 16, 1, 1, NOW(), NOW(), NOW(), 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `course` WHERE `id` = 2000002);

UPDATE `course`
SET `cover_url` = 'http://localhost:9000/novamind-assets/course-covers/course_spring_boot_01.jpg',
    `update_time` = NOW()
WHERE `id` = 2000001;

UPDATE `course`
SET `cover_url` = 'http://localhost:9000/novamind-assets/course-covers/course_ui_ux_design_01.jpg',
    `update_time` = NOW()
WHERE `id` = 2000002;

USE `novamind_user`;

INSERT INTO `user` (`id`, `username`, `cell_phone`, `password`, `status`, `type`, `create_time`, `update_time`, `creater`, `updater`)
SELECT 10001, 'jack', '13500010003', '$2a$10$tyJiYdep22cCwva5ZhyQX.NWK8SyVT/C0MZN3rLbEk1EWXMU1m2Bu', 1, 2, NOW(), NOW(), 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `id` = 10001 OR `username` = 'jack' OR `cell_phone` = '13500010003');

INSERT INTO `user_detail` (`id`, `type`, `name`, `gender`, `icon`, `email`, `qq`, `birthday`, `job`, `province`, `city`, `district`, `intro`, `photo`, `role_id`, `create_time`, `update_time`, `creater`, `updater`, `dep_id`)
SELECT 10001, 2, 'Jack', 1, 'http://localhost:9000/novamind-assets/user-avatars/avatar_student_male_01.jpg', 'jack@example.com', NULL, '2000-01-01', '学生', '上海市', '上海市', '浦东新区', '本地联调用测试学员账号', NULL, 2, NOW(), NOW(), 1, 1, NULL
WHERE NOT EXISTS (SELECT 1 FROM `user_detail` WHERE `id` = 10001);

UPDATE `user_detail`
SET `icon` = 'http://localhost:9000/novamind-assets/user-avatars/avatar_student_male_01.jpg',
    `update_time` = NOW()
WHERE `id` = 10001;
