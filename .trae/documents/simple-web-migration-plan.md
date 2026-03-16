# 前端独立项目迁移计划

本计划旨在将 `main-service` 中的 `chat_stream.html` 迁移为一个独立的 Vue 3 前端项目 `simple_web`，位于工作区根目录。该项目将使用 Vite 构建，并配置代理以连接后端服务。

## 目标

1. 创建 `simple_web` 目录及 Vue 3 项目结构。
2. 迁移 `chat_stream.html` 的 HTML、CSS 和 JS 逻辑到 Vue 组件。
3. 配置 Vite 开发服务器代理，转发 API 请求到后端 (端口 9090)。
4. 确保新项目可以独立启动并正常运行。

## 实施步骤

### 1. 初始化项目结构

* 创建 `simple_web` 目录。

* 创建 `package.json`，包含 `vue`、`vite` 等依赖。

* 创建 `vite.config.js`，配置 Vue 插件和服务器代理。

* 创建 `index.html` 作为入口文件。

* 创建 `src` 目录，包含 `main.js` 和 `App.vue`。

### 2. 迁移代码

* **HTML 模板**: 将 `chat_stream.html` 中的 `<body>` 内容迁移到 `src/App.vue` 的 `<template>` 部分。

* **CSS 样式**: 将 `<style>` 内容迁移到 `src/App.vue` 的 `<style scoped>` 部分。

* **JS 逻辑**: 将 `<script>` 中的逻辑（`setup` 函数内容）迁移到 `src/App.vue` 的 `<script setup>` 部分。

  * 移除 Vue CDN 引用，改为 `import` 语法。

  * 调整 API 请求路径（由于配置了代理，相对路径 `/api` 依然适用，但需确保代理配置正确）。

### 3. 配置开发环境

* 在 `vite.config.js` 中设置 `server.proxy`，将 `/api` 转发至 `http://localhost:9090`。

* 配置构建脚本 `dev`, `build`, `preview`。

### 4. 验证与运行

* 运行 `npm install` 安装依赖。

* 运行 `npm run dev` 启动开发服务器。

* 验证页面功能是否与原版一致（流式对话、API 调用）。

## 依赖版本参考

* `vue`: ^3.x

* `vite`: ^5.x

* `@vitejs/plugin-vue`: ^5.x

