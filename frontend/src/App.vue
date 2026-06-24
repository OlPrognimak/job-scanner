<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">Job Scanner</div>
      <nav>
        <RouterLink to="/jobs">Jobs</RouterLink>
        <RouterLink to="/search-criteria">Search Criteria</RouterLink>
      </nav>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
