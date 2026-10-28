<script setup>
import { defineAsyncComponent } from 'vue'
import { getAuth } from '../../stores/community'

// 按登录身份渲染不同告警页面：
// 普通用户 → 告警查询/处置视图；管理员/超级管理员 → 告警管理视图
const role = getAuth().role
const isAdmin = role === '管理员' || role === '超级管理员'
const Page = isAdmin
  ? defineAsyncComponent(() => import('./AlarmManage.vue'))
  : defineAsyncComponent(() => import('./AlarmQuery.vue'))
</script>

<template>
  <component :is="Page" />
</template>
