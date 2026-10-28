<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  icon: { type: String, default: '' }
})
</script>

<template>
  <header class="page-header">
    <div class="ph-main">
      <div v-if="icon" class="ph-icon">
        <el-icon :size="20"><component :is="icon" /></el-icon>
      </div>
      <div class="ph-text">
        <h1 class="ph-title">{{ title }}</h1>
        <p v-if="subtitle" class="ph-sub">{{ subtitle }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="ph-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.ph-main {
  display: flex;
  align-items: center;
  gap: 13px;
  min-width: 0;
}

.ph-icon {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(145deg, var(--primary-tint), var(--primary-tint-2));
  color: var(--primary);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9), 0 2px 8px rgba(47, 111, 137, 0.1);
}

.ph-text {
  min-width: 0;
}

.ph-title {
  margin: 0;
  font-size: 21px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--text-main);
  letter-spacing: 0.6px;
}

.ph-sub {
  margin: 4px 0 0;
  font-size: 12.5px;
  line-height: 1.45;
  color: var(--text-soft);
  letter-spacing: 0.3px;
}

.ph-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 600px) {
  .ph-title {
    font-size: 18px;
  }

  .ph-icon {
    width: 38px;
    height: 38px;
  }

  .ph-actions {
    width: 100%;
  }
}
</style>
