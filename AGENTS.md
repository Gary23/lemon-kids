# 项目协作约定

- 所有面向用户的回复使用简体中文。
- 修改 `cloud-functions/evaluate-reading/` 中会影响腾讯云 SCF 部署的源码、依赖或启动文件后，结束任务前必须生成一个新的可部署 ZIP。文件名使用 `evaluate-reading-web-YYYYMMDD-功能名.zip`，并保证 ZIP 根目录直接包含 `index.js`、`server.js`、`scf_bootstrap`、`package.json`、`package-lock.json` 和生产依赖 `node_modules/`。
- 生成部署 ZIP 后，必须用 `unzip -l` 核验其根目录结构，并在 `cloud-functions/evaluate-reading/README.md` 中更新当前部署包文件名。
