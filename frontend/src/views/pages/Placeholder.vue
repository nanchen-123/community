<script setup>
defineProps({
  title: { type: String, default: '' }
})
</script>

<template>
  <div>
    <div class="page-title">{{ title }}</div>
    <div class="placeholder">
      <el-empty :description="title + ' 模块开发中...'" :image-size="120" />
    </div>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 20px;
  letter-spacing: 1px;
}
.placeholder {
  background: var(--card-bg);
  border-radius: 10px;
  box-shadow: 0 1px 3px var(--shadow);
  padding: 60px 20px;
}
</style>
