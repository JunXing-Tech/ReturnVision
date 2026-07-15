import { ref, onMounted, onUnmounted } from 'vue';

/**
 * 响应式工具，用于判断当前是否处于移动端视图
 * 默认使用 768px 作为移动端断点
 */
export function useMobile(breakpoint = 768) {
  const isMobile = ref(false);

  const check = () => {
    isMobile.value = window.innerWidth <= breakpoint;
  };

  onMounted(() => {
    check();
    window.addEventListener('resize', check);
  });

  onUnmounted(() => {
    window.removeEventListener('resize', check);
  });

  return { isMobile };
}
