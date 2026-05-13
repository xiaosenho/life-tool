# Scripts

本目录用于保存项目自动化脚本。

建议脚本：

- `dev-backend.sh`：启动后端服务。
- `dev-frontend.sh`：启动 Expo App。
- `test-backend.sh`：执行后端测试。
- `test-frontend.sh`：执行前端 lint/build。
- `check-all.sh`：本地全量检查。

脚本加入前需要保证：

- 可重复执行。
- 不依赖个人本机绝对路径。
- 失败时返回非 0 状态码。
- 不包含敏感信息。
