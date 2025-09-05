<script setup>
import QuizGenerator from './components/QuizGenerator.vue'
</script>

<template>
  <div id="app">
    <header>
      <h1>Auknowlog Quiz Generator</h1>
    </header>

    <main>
      <QuizGenerator />
    </main>
  </div>
</template>

<style>
/* Global styles */
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', sans-serif;
  background-color: #f5f7fa;
  line-height: 1.6;
}

#app {
  width: 100%;
  min-height: 100vh;
}

header {
  width: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 10px 20px;
  text-align: center;
  margin-bottom: 15px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

header h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

main {
  width: 100%;
  margin: 0;
  padding: 0;
  display: block;
}
</style>