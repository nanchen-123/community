<script setup>
import { defineAsyncComponent } from 'vue'
import { getAuth } from '../../stores/community'

// 按登录身份渲染不同巡检任务页面：
// 普通用户 → 任务创建/下发/历史记录视图；管理员/超级管理员 → 任务管理视图
const role = getAuth().role
const isAdmin = role === '管理员' || role === '超级管理员'
const Page = isAdmin
  ? defineAsyncComponent(() => import('./TaskManage.vue'))
  : defineAsyncComponent(() => import('./TaskQuery.vue'))
</script>

<template>
  <component :is="Page" />
</template>
