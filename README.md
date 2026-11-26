# Club Welcome System · 社团迎新与报名管理平台

基于 Java 17、Spring Boot 3.5、MySQL 8 和原生 JavaScript 的完整 Web 实践项目。
支持兴趣选择、报名、纪念卡、活动投票及后台管理。前端通过 JSON 接口访问 Java 后端，由 JDBC 操作 MySQL。

## 项目说明

本项目在 AI 辅助下完成开发与迭代，用于学习 Web 开发、前后端联调和数据库迁移。
本仓库为公开演示版本：已替换机构名称、校徽、真实群邀请及域名，不包含真实报名数据或运行凭据。QQ群链接是占位示例，使用前请替换。

## 功能

- 兴趣多选、资料校验、报名重试去重。
- 生成报名纪念卡，投票及改选。
- 浏览器记忆报名进度，并向后端验证凭证。
- 管理员登录、分页搜索、统计、CSV 导出和批量删除。
- 密码摘要、会话、CSRF 校验、请求限流、定时到期清理。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | HTML、CSS、原生 JavaScript |
| 后端 | Spring Boot、Spring MVC、Jackson、内嵌 Tomcat |
| 数据库 | MySQL 8、JDBC、InnoDB、utf8mb4 |
| 测试 | JUnit 5、Spring Boot Test、MockMvc、Node.js 测试运行器 |

未使用 Spring Security、MyBatis 或 JPA；登录与数据访问逻辑由项目代码实现。当前按单实例设计，未声称支持高并发或生产级多实例部署。

## 快速开始

准备 JDK 17、Maven 3.6.3+、MySQL 8.0。

1. 在 MySQL 客户端执行 `sql/create-database.sql`，创建 `club_welcome` 库。
2. 运行 `mvn -DskipTests package`，生成 `target/welcome.jar`。这一步跳过测试。
3. 在系统终端运行 `java -jar target/welcome.jar --setup --local`，设置网站管理员账号和密码（8～64 位，包含大小写字母和数字）。
4. 编辑生成的 `.env`，填写 `DATABASE_URL`、`DATABASE_USERNAME`、`DATABASE_PASSWORD`。为数据库账号分配该库所需权限；账号和密码不应提交 Git。
5. 运行 `java -jar target/welcome.jar`，访问 http://127.0.0.1:8080/ ，后台为 http://127.0.0.1:8080/admin 。

`.env.example` 是配置字段示例，不含默认管理员密码。已有 `.env` 时初始化工具拒绝覆盖。公网使用时需配置 HTTPS、BASE_URL 和 COOKIE_SECURE。

## 测试

测试必须使用单独的 `club_welcome_test` 数据库，会清空该库业务表。

```powershell
$env:TEST_DATABASE_URL='jdbc:mysql://127.0.0.1:3306/club_welcome_test?characterEncoding=UTF-8'
$env:TEST_DATABASE_USERNAME='你的测试账号'
$env:TEST_DATABASE_PASSWORD='你的测试密码'
mvn verify
node --test tests/frontend-progress.test.cjs
```

## 项目结构

- `site/`：网页、样式和交互脚本。
- `src/main/java/cn/club/welcome/`：接口、业务、会话和数据访问。
- `src/main/resources/schema-mysql.sql`：建表结构。
- `src/test/`、`tests/`：后端和前端测试。
- `scripts/migrate_sqlite.py`：旧 SQLite 数据只读迁移到空 MySQL 库的工具。

数据库迁移需停止写入、先备份，保留相同 APP_SECRET 才能继续验证旧报名凭证。脚本在终端隐藏输入 MySQL 密码，拒绝非空目标库；迁移后核对记录数。应用会按保存期限清理过期记录。

## 修改与维护

- 文案和群配置：`site/content.js`；默认群链接：`PortalService.java`。
- 样式：`site/theme.css`。
- 重置后台密码：停止应用后运行 `java -jar target/welcome.jar --reset-password`，随后重启。
- MySQL 备份使用 `mysqldump --single-transaction`，不要使用旧 SQLite 的备份命令。

真实数据库、密码、报名名单、备份和日志不得上传仓库。GitHub 托管的是源码，运行网站仍需要 Java 服务和 MySQL。
