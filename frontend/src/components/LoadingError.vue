<script setup lang="ts">
defineProps<{ loading?: boolean; error?: string }>()
</script>

<template>
  <p v-if="loading" class="muted">Lade Daten...</p>
  <p v-else-if="error" class="error">{{ error }}</p>
</template>
