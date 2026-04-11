<template>
  <!-- 登录页 -->
  <div v-if="!isLogin" class="login-container">
    <div class="login-box">
      <h2>Prompt服务单元（PSU）管理平台 V1.0</h2>
      <p class="secret-tip">内部研发涉密 · 禁止对外同步</p>
      <el-form :model="loginForm" label-width="80px" style="margin-top: 30px;">
        <el-form-item label="账号">
          <el-input v-model="loginForm.username" placeholder="请输入账号"></el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码"></el-input>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="loginForm.role" placeholder="请选择角色" style="width: 100%;">
            <el-option label="超级管理员" value="admin"></el-option>
            <el-option label="后端研发" value="developer"></el-option>
            <el-option label="产品/运营" value="product"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%;" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>

  <!-- 主框架 -->
  <div v-else class="main-container">
    <!-- 顶部导航 -->
    <el-header class="header">
      <div class="header-left">
        <h3>PSU管理平台</h3>
        <el-tag type="info" style="margin-left: 15px;">当前角色：{{ roleName }}</el-tag>
      </div>
      <div class="header-right">
        <el-button @click="handleLogout" type="danger" text>退出登录</el-button>
      </div>
    </el-header>

    <el-container class="content-container">
      <!-- 侧边栏菜单 -->
      <el-aside width="220px" class="aside">
        <el-menu
          :default-active="activeMenu"
          class="menu"
          @select="handleMenuSelect"
        >
          <!-- 管理员菜单 -->
          <el-menu-item v-if="currentRole === 'admin'" index="user-manage">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item v-if="currentRole === 'admin'" index="system-config">
            <el-icon><Setting /></el-icon>
            <span>系统配置</span>
          </el-menu-item>
          <el-menu-item v-if="currentRole === 'admin'" index="audit-log">
            <el-icon><Document /></el-icon>
            <span>审计日志</span>
          </el-menu-item>

          <!-- 研发菜单 -->
          <el-menu-item v-if="currentRole === 'developer'" index="psu-manage">
            <el-icon><Box /></el-icon>
            <span>PSU资产管理</span>
          </el-menu-item>
          <el-menu-item v-if="currentRole === 'developer'" index="schema-edit">
            <el-icon><Code /></el-icon>
            <span>Schema编辑器</span>
          </el-menu-item>
          <el-menu-item v-if="currentRole === 'developer'" index="prompt-auth">
            <el-icon><Lock /></el-icon>
            <span>Prompt权限管控</span>
          </el-menu-item>
          <el-menu-item v-if="currentRole === 'developer'" index="version-audit">
            <el-icon><Check /></el-icon>
            <span>版本审核</span>
          </el-menu-item>
          <el-menu-item v-if="currentRole === 'developer'" index="code-generate">
            <el-icon><Cpu /></el-icon>
            <span>业务代码生成</span>
          </el-menu-item>

          <!-- 产品/运营菜单 -->
          <el-menu-item v-if="currentRole === 'product'" index="prompt-edit">
            <el-icon><EditPen /></el-icon>
            <span>Prompt编排调试</span>
          </el-menu-item>
          <el-menu-item v-if="currentRole === 'product'" index="test-case">
            <el-icon><ListCheck /></el-icon>
            <span>测试用例管理</span>
          </el-menu-item>
          <el-menu-item v-if="currentRole === 'product'" index="version-submit">
            <el-icon><Upload /></el-icon>
            <span>版本提审</span>
          </el-menu-item>

          <!-- 通用菜单 -->
          <el-menu-item index="version-trace">
            <el-icon><Time /></el-icon>
            <span>版本台账</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-main class="main-content">
        <!-- 管理员-用户管理 -->
        <div v-if="activeMenu === 'user-manage' && currentRole === 'admin'">
          <h2>用户管理</h2>
          <el-button type="primary" style="margin: 15px 0;">新增用户</el-button>
          <el-table :data="userList" border style="width: 100%;">
            <el-table-column prop="username" label="账号" width="180"></el-table-column>
            <el-table-column prop="roleName" label="角色" width="150"></el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope">
                <el-tag :type="scope.row.status === '启用' ? 'success' : 'danger'">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作">
              <el-button type="primary" text>重置密码</el-button>
              <el-button type="danger" text>停用</el-button>
            </el-table-column>
          </el-table>
        </div>

        <!-- 管理员-系统配置 -->
        <div v-if="activeMenu === 'system-config' && currentRole === 'admin'">
          <h2>系统配置（仅管理员可见）</h2>
          <el-alert type="warning" title="仅可配置大模型API密钥，无其他冗余配置项" style="margin: 15px 0;"></el-alert>
          <el-table :data="apiConfigList" border style="width: 100%;margin-top: 20px;">
            <el-table-column prop="provider" label="模型厂商" width="180"></el-table-column>
            <el-table-column prop="apiKeyMask" label="API密钥" width="400"></el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope">
                <el-tag :type="scope.row.status === '启用' ? 'success' : 'danger'">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作">
              <el-button type="primary" text>编辑</el-button>
            </el-table-column>
          </el-table>
          <el-button type="primary" style="margin-top: 20px;">新增厂商配置</el-button>
        </div>

        <!-- 研发-PSU资产管理 -->
        <div v-if="activeMenu === 'psu-manage' && currentRole === 'developer'">
          <h2>PSU资产管理</h2>
          <el-button type="primary" style="margin: 15px 0;">新建PSU</el-button>
          <el-table :data="psuList" border style="width: 100%;">
            <el-table-column prop="psuId" label="PSU唯一ID" width="220"></el-table-column>
            <el-table-column prop="name" label="PSU名称" width="200"></el-table-column>
            <el-table-column prop="version" label="最新版本" width="120"></el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope">
                <el-tag :type="statusTypeMap[scope.row.status]">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" width="180"></el-table-column>
            <el-table-column label="操作" width="350">
              <el-button type="primary" text>编辑Schema</el-button>
              <el-button type="primary" text>权限管控</el-button>
              <el-button type="success" text>审核版本</el-button>
              <el-button type="warning" text>生成代码</el-button>
              <el-button type="info" text>台账</el-button>
            </el-table-column>
          </el-table>
        </div>

        <!-- 研发-Schema编辑器 -->
        <div v-if="activeMenu === 'schema-edit' && currentRole === 'developer'">
          <h2>JSON Schema专属编辑器（仅研发可编辑）</h2>
          <el-alert type="info" title="业务侧无此页面入口，仅研发/管理员可访问编辑" style="margin: 15px 0;"></el-alert>
          <el-row :gutter="20" style="margin-top: 20px;">
            <el-col :span="12">
              <h4>可视化字段配置</h4>
              <el-button type="primary" size="small" style="margin: 10px 0;">新增字段</el-button>
              <el-table :data="schemaFieldList" border style="width: 100%;">
                <el-table-column prop="fieldName" label="字段名" width="150"></el-table-column>
                <el-table-column prop="fieldType" label="字段类型" width="120"></el-table-column>
                <el-table-column prop="required" label="必填" width="80">
                  <template #default="scope">
                    <el-checkbox v-model="scope.row.required" disabled></el-checkbox>
                  </template>
                </el-table-column>
                <el-table-column label="操作">
                  <el-button type="primary" text>编辑</el-button>
                  <el-button type="danger" text>删除</el-button>
                </el-table-column>
              </el-table>
            </el-col>
            <el-col :span="12">
              <h4>Schema JSON预览</h4>
              <el-input type="textarea" :rows="20" v-model="schemaJson" readonly style="width: 100%;font-family: monospace;"></el-input>
            </el-col>
          </el-row>
        </div>

        <!-- 产品-Prompt编排调试 -->
        <div v-if="activeMenu === 'prompt-edit' && currentRole === 'product'">
          <h2>Prompt可视化编排调试</h2>
          <el-alert type="warning" title="仅可编辑研发开放的Prompt片段，Schema结构仅可读，不可编辑" style="margin: 15px 0;"></el-alert>
          
          <el-row :gutter="20">
            <el-col :span="16">
              <h4>Prompt编辑器</h4>
              <div v-for="fragment in promptFragmentList" :key="fragment.fragmentId" style="margin: 15px 0;border: 1px solid #dcdfe6;border-radius: 4px;padding: 15px;">
                <div style="display: flex;justify-content: space-between;align-items: center;margin-bottom: 10px;">
                  <span><strong>{{ fragment.name }}</strong></span>
                  <el-tag v-if="!fragment.editable" type="info">研发锁定-不可编辑</el-tag>
                  <el-tag v-else type="success">可编辑</el-tag>
                </div>
                <el-input 
                  type="textarea" 
                  :rows="4" 
                  v-model="fragment.content" 
                  :readonly="!fragment.editable"
                  style="width: 100%;font-family: monospace;"
                ></el-input>
              </div>
              <el-button type="primary" style="margin-right: 10px;">保存草稿</el-button>
              <el-button type="success">调试运行</el-button>
            </el-col>

            <el-col :span="8">
              <h4>Schema字段台账（仅可读）</h4>
              <el-table :data="schemaFieldList" border size="small" style="width: 100%;margin-bottom: 20px;">
                <el-table-column prop="fieldName" label="字段名" width="120"></el-table-column>
                <el-table-column prop="fieldType" label="类型" width="100"></el-table-column>
              </el-table>

              <h4>测试入参</h4>
              <el-form :model="testInput" label-width="100px" style="margin-top: 10px;">
                <el-form-item label="商品名称">
                  <el-input v-model="testInput.product_name" placeholder="请输入商品名称"></el-input>
                </el-form-item>
                <el-form-item label="商品售价">
                  <el-input-number v-model="testInput.product_price" :min="0.01" style="width: 100%;"></el-input-number>
                </el-form-item>
                <el-form-item label="用户等级">
                  <el-select v-model="testInput.user_level" style="width: 100%;">
                    <el-option label="普通" value="普通"></el-option>
                    <el-option label="VIP" value="VIP"></el-option>
                    <el-option label="SVIP" value="SVIP"></el-option>
                  </el-select>
                </el-form-item>
              </el-form>

              <h4 style="margin-top: 20px;">调试返回结果</h4>
              <el-input type="textarea" :rows="10" readonly v-model="debugResult" style="width: 100%;"></el-input>
            </el-col>
          </el-row>
        </div>

        <!-- 研发-代码生成 -->
        <div v-if="activeMenu === 'code-generate' && currentRole === 'developer'">
          <h2>业务代码生成（仅核心业务代码）</h2>
          <el-alert type="warning" title="仅生成参数校验、入参拼装、Prompt组装核心Java代码，不生成Controller/接口/部署脚本" style="margin: 15px 0;"></el-alert>
          
          <el-row :gutter="20" style="margin-top: 20px;">
            <el-col :span="6">
              <h4>生成配置</h4>
              <el-form label-width="100px">
                <el-form-item label="PSU选择">
                  <el-select v-model="codeGenerateConfig.psuId" placeholder="请选择PSU" style="width: 100%;">
                    <el-option label="营销文案生成器" value="marketing_copy_generator"></el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="包名">
                  <el-input v-model="codeGenerateConfig.packageName" placeholder="请输入包名"></el-input>
                </el-form-item>
                <el-form-item label="类名">
                  <el-input v-model="codeGenerateConfig.className" placeholder="请输入类名"></el-input>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="generateCode">一键生成代码</el-button>
                </el-form-item>
              </el-form>
            </el-col>
            <el-col :span="18">
              <h4>生成的Java代码预览</h4>
              <el-input type="textarea" :rows="25" v-model="generateCodeResult" readonly style="width: 100%;font-family: monospace;"></el-input>
              <el-button type="success" style="margin-top: 15px;margin-right: 10px;">下载Java文件</el-button>
              <el-button type="primary">登记Git入库信息</el-button>
            </el-col>
          </el-row>
        </div>

        <!-- 403无权限提示 -->
        <div v-if="!menuAuthMap[activeMenu]">
          <el-empty description="您无此页面的访问权限"></el-empty>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'

// 登录状态
const isLogin = ref(false)
const currentRole = ref('')
const roleName = computed(() => {
  const map = {
    admin: '超级管理员',
    developer: '后端研发',
    product: '产品/运营'
  }
  return map[currentRole.value] || '未知角色'
})

// 登录表单
const loginForm = reactive({
  username: '',
  password: '',
  role: ''
})

// 菜单管理
const activeMenu = ref('')
const menuAuthMap = reactive({
  // 管理员菜单
  'user-manage': ['admin'],
  'system-config': ['admin'],
  'audit-log': ['admin'],
  // 研发菜单
  'psu-manage': ['developer'],
  'schema-edit': ['developer'],
  'prompt-auth': ['developer'],
  'version-audit': ['developer'],
  'code-generate': ['developer'],
  // 产品菜单
  'prompt-edit': ['product'],
  'test-case': ['product'],
  'version-submit': ['product'],
  // 通用菜单
  'version-trace': ['admin', 'developer', 'product']
})

// 模拟数据-用户列表
const userList = ref([
  { username: 'admin', roleName: '超级管理员', status: '启用' },
  { username: 'dev01', roleName: '后端研发', status: '启用' },
  { username: 'product01', roleName: '产品/运营', status: '启用' }
])

// 模拟数据-API配置
const apiConfigList = ref([
  { provider: '字节跳动豆包', apiKeyMask: 'sk-****************************1234', status: '启用' },
  { provider: 'OpenAI', apiKeyMask: 'sk-****************************abcd', status: '启用' }
])

// 模拟数据-PSU列表
const statusTypeMap = {
  '草稿': 'info',
  '调试中': 'primary',
  '待审核': 'warning',
  '已定稿': 'success',
  '已归档': 'info'
}
const psuList = ref([
  { psuId: 'marketing_copy_generator', name: '电商营销文案生成器', version: '1.2.0', status: '待审核', updateTime: '2026-04-10 15:30:00' },
  { psuId: 'customer_service_reply', name: '智能客服话术生成', version: '1.0.0', status: '已定稿', updateTime: '2026-04-09 10:00:00' }
])

// 模拟数据-Schema字段列表
const schemaFieldList = ref([
  { fieldName: 'product_name', fieldType: '字符串', required: true },
  { fieldName: 'product_price', fieldType: '数字', required: true },
  { fieldName: 'user_level', fieldType: '枚举', required: false }
])

// 模拟数据-Schema JSON
const schemaJson = ref(`{
  "type": "object",
  "title": "营销文案生成入参",
  "properties": {
    "product_name": {
      "type": "string",
      "title": "商品名称",
      "required": true
    },
    "product_price": {
      "type": "number",
      "title": "商品售价",
      "required": true
    },
    "user_level": {
      "type": "string",
      "title": "用户等级",
      "enum": ["普通", "VIP", "SVIP"]
    }
  }
}`)

// 模拟数据-Prompt片段列表
const promptFragmentList = ref([
  { fragmentId: 'role', name: '角色设定', content: '你是拥有10年经验的资深电商营销文案专家，擅长写高转化率的商品营销文案。', editable: false },
  { fragmentId: 'product_info', name: '商品基础信息', content: '本次推广的商品是：{{product_name}}，售价{{product_price}}元。', editable: true },
  { fragmentId: 'user_level', name: '用户等级专属内容', content: '本次文案面向{{user_level}}用户，需突出对应权益与性价比。', editable: true },
  { fragmentId: 'output_format', name: '输出格式要求', content: '请严格按照JSON格式返回，禁止输出其他内容。', editable: false }
])

// 模拟数据-测试入参
const testInput = reactive({
  product_name: '主动降噪无线蓝牙耳机',
  product_price: 299,
  user_level: 'SVIP'
})

// 调试结果
const debugResult = ref('')

// 代码生成配置
const codeGenerateConfig = reactive({
  psuId: '',
  packageName: 'com.ai.psu.marketing',
  className: 'MarketingCopyGenerator'
})

// 生成的代码结果
const generateCodeResult = ref('')

// 登录处理
const handleLogin = () => {
  if (!loginForm.username || !loginForm.password || !loginForm.role) {
    ElMessage.error('请填写完整登录信息')
    return
  }
  isLogin.value = true
  currentRole.value = loginForm.role
  // 登录后默认菜单
  if (currentRole.value === 'admin') {
    activeMenu.value = 'user-manage'
  } else if (currentRole.value === 'developer') {
    activeMenu.value = 'psu-manage'
  } else {
    activeMenu.value = 'prompt-edit'
  }
  ElMessage.success('登录成功')
}

// 退出登录
const handleLogout = () => {
  isLogin.value = false
  currentRole.value = ''
  activeMenu.value = ''
  loginForm.username = ''
  loginForm.password = ''
  loginForm.role = ''
  ElMessage.success('退出登录成功')
}

// 菜单切换
const handleMenuSelect = (index) => {
  // 权限校验
  if (menuAuthMap[index] && !menuAuthMap[index].includes(currentRole.value)) {
    ElMessage.error('您无此页面的访问权限')
    return
  }
  activeMenu.value = index
}

// 代码生成
const generateCode = () => {
  if (!codeGenerateConfig.psuId) {
    ElMessage.error('请选择PSU')
    return
  }
  generateCodeResult.value = `package ${codeGenerateConfig.packageName};

import java.util.Map;
import java.util.StringJoiner;

/**
 * 营销文案生成器 - PSU自动生成代码
 * PSU_ID: ${codeGenerateConfig.psuId}
 * 版本号: 1.2.0
 * 生成时间: ${new Date().toLocaleString()}
 * 注意：本代码仅为核心业务逻辑，不包含Controller/接口层代码
 */
public class ${codeGenerateConfig.className} {

    /**
     * 入参合法性校验
     * @param input 入参Map
     * @return 校验结果
     */
    public String validateInput(Map<String, Object> input) {
        // 商品名称必填校验
        if (!input.containsKey("product_name") || input.get("product_name") == null) {
            return "商品名称不能为空";
        }
        // 商品售价必填与数值校验
        if (!input.containsKey("product_price") || input.get("product_price") == null) {
            return "商品售价不能为空";
        }
        try {
            double price = Double.parseDouble(input.get("product_price").toString());
            if (price <= 0) {
                return "商品售价必须大于0";
            }
        } catch (Exception e) {
            return "商品售价必须为合法数字";
        }
        return "SUCCESS";
    }

    /**
     * 完整Prompt组装逻辑
     * @param input 入参Map
     * @return 最终组装完成的Prompt
     */
    public String assemblePrompt(Map<String, Object> input) {
        StringJoiner promptJoiner = new StringJoiner("\\n\\n");
        
        // 角色设定片段（锁定不可编辑）
        promptJoiner.add("你是拥有10年经验的资深电商营销文案专家，擅长写高转化率的商品营销文案。");
        
        // 商品基础信息片段
        String productInfo = String.format("本次推广的商品是：%s，售价%s元。",
                input.getOrDefault("product_name", ""),
                input.getOrDefault("product_price", ""));
        promptJoiner.add(productInfo);
        
        // 用户等级专属内容片段
        String userLevel = input.getOrDefault("user_level", "普通").toString();
        String userLevelContent = String.format("本次文案面向%s用户，需突出对应权益与性价比。", userLevel);
        promptJoiner.add(userLevelContent);
        
        // 输出格式要求片段（锁定不可编辑）
        promptJoiner.add("请严格按照JSON格式返回，禁止输出其他内容。");
        
        return promptJoiner.toString();
    }
}`
  ElMessage.success('代码生成成功')
}
</script>

<style scoped>
.login-container {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-box {
  width: 450px;
  background: #fff;
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.2);
}
.login-box h2 {
  text-align: center;
  margin: 0;
  color: #303133;
}
.secret-tip {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin: 10px 0 0 0;
}

.main-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
}
.header-left h3 {
  margin: 0;
  color: #303133;
}
.content-container {
  flex: 1;
  overflow: hidden;
}
.aside {
  background: #304156;
  border-right: 1px solid #e4e7ed;
}
.menu {
  border: none;
  background: #304156;
  color: #fff;
}
.menu .el-menu-item {
  color: #fff;
}
.menu .el-menu-item:hover {
  background: #263445 !important;
}
.menu .el-menu-item.is-active {
  background: #409eff !important;
  color: #fff;
}
.main-content {
  background: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}
.main-content h2 {
  margin: 0 0 10px 0;
  color: #303133;
  font-size: 20px;
}
</style>