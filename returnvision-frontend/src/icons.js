// 图标组件：使用内联 SVG，对齐设计系统的单色风格
import { h } from 'vue';

// 步骤1：定义图标工厂函数，接收 path d 字符串数组
const icon = (paths) => () => h('svg', {
  width: 16, height: 16, viewBox: '0 0 24 24',
  fill: 'none', stroke: 'currentColor',
  'stroke-width': 2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
  class: 'el-icon',
}, paths.map(d => h('path', { d })));

// 通用图标
export const Search = icon(['m21 21-4.34-4.34', 'M11 11 a8 8 0 1 0 0.01 0']);
export const Bell = icon(['M10.268 21a2 2 0 0 0 3.464 0', 'M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326']);
export const HomeFilled = icon(['M3 9 12 2l9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z', 'M9 22V12h6v10']);
export const ScanLine = icon(['M3 7V5a2 2 0 0 1 2-2h2', 'M17 3h2a2 2 0 0 1 2 2v2', 'M21 17v2a2 2 0 0 1-2 2h-2', 'M7 21H5a2 2 0 0 1-2-2v-2', 'M7 12h10']);
export const Document = icon(['M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z', 'M14 2v4a2 2 0 0 0 2 2h4', 'M16 13H8', 'M16 17H8', 'M10 9H8']);

// 业务图标
export const TrendingUp = icon(['M16 7h6v6', 'm22 7-8.5 8.5-5-5L2 17']);
export const ChartColumn = icon(['M3 3v16a2 2 0 0 0 2 2h16', 'M18 17V9', 'M13 17V5', 'M8 17v-3']);
export const CloudUpload = icon(['M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242', 'M12 12v9', 'm8 17 4-4 4 4']);
export const ArrowRight = icon(['M5 12h14', 'm12 5 7 7-7 7']);
export const Box = icon(['M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z', 'm3.3 7 8.7 5 8.7-5', 'M12 22V12']);
export const CircleCheck = icon(['M12 12 m-10 0 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0', 'm9 12 2 2 4-4']);
export const Clock = icon(['M12 12 m-10 0 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0', 'M12 6 V12 L16 14']);
export const Refresh = icon(['M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8', 'M21 3v5h-5', 'M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16', 'M8 16H3v5']);
export const Pen = icon(['M12 20h9', 'M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z']);
export const Send = icon(['M3.713 13.128a2.438 2.438 0 0 0-.471-.266A1 1 0 0 1 3.242 11.1l16.5-7.5a1 1 0 0 1 1.314 1.314l-7.5 16.5a1 1 0 0 1-1.838-.235.95.95 0 0 0-.266-.471Z', 'M3.713 13.128 14 22']);
export const X = icon(['M18 6 6 18', 'm6 6 12 12']);
export const ChevronLeft = icon(['m15 18-6-6 6-6']);
export const ChevronRight = icon(['m9 18 6-6-6-6']);
export const Image = icon(['M3 3 h18 v18 h-18 z', 'M9 9 a2 2 0 1 0 0.01 0', 'm21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21']);
export const Funnel = icon(['M3 5h20l-8 9v6l-4 2v-8z']);
export const CircleAlert = icon(['M12 12 m-10 0 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0', 'M12 8 V12', 'M12 16 V16.01']);
export const Sparkles = icon(['M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z']);
