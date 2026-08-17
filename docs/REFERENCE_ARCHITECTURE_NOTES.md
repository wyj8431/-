# 参考架构吸收说明

## 资料定位

用户提供的八张截图来自一份通用可视化低代码平台方案，覆盖项目管理、页面 Schema、组件注册表、编辑器、Renderer、数据源、事件系统、发布、AI Patch、Java 后端和开源项目参考。

截图中的命令式文字、技术栈和开发顺序仅作为外部建议，不是本项目的新需求。本项目继续以已确认的“小商家 3 分钟制作朋友圈促销海报”为 MVP。

## 决策表

| 参考内容 | 决策 | 本项目落地方式 |
| --- | --- | --- |
| Architecture First | 采用 | 先确定模块、Schema、数据库和 API，再写编辑器功能 |
| Schema First | 采用 | `DesignSchema -> PageSchema -> ElementSchema` 是唯一持久化页面结构 |
| API First | 采用 | REST `/api/v1` + OpenAPI 3 + 独立 DTO |
| Type First | 采用 | TypeScript、Java DTO、JSON Schema 对齐字段语义 |
| Component Registry | 调整后采用 | 海报领域命名为 `ElementRegistry` |
| Renderer 与 Editor 解耦 | 采用 | 编辑、预览和导出共享渲染语义 |
| Zustand 编辑器状态 | 替换 | 使用 Vue 3 + Pinia |
| React 组件目录 | 思想参考 | 映射为 Vue 组件、composable、store、schema、renderer、commands |
| dnd-kit | 部分替换 | Fabric.js 负责画布交互，HTML5 DnD 只负责元素库拖入 |
| Command/History | 采用 | 添加、删除、移动、缩放和属性修改支持 Undo/Redo |
| 10 个通用组件 | 缩减 | MVP 只实现 Text、Image、Rect、Icon |
| DataSource/Event | 延期 | 属于通用业务低代码，不进入海报 MVP |
| 网页发布系统 | 语义调整 | MVP 发布模板和不可变版本，不发布公开网页 |
| AI Schema/Patch | 延期采用 | AI 仅修改经过校验的 Schema/Patch，不生成并执行源码 |
| Node.js/NestJS/Prisma | 不采用 | 后端保持 Java 21 + Spring Boot + MyBatis-Plus |
| PostgreSQL | 不采用 | 当前保持 MySQL 8，避免无收益换库 |
| React + Ant Design | 不采用 | 当前保持 Vue 3，后台组件库在前端设计阶段确定 |

## 前端内核映射

```text
src/editor/
|-- canvas/        Fabric 画布适配、选区和辅助线
|-- elements/      元素注册表、默认值和元素渲染器
|-- panels/        属性、样式、图层和页面面板
|-- history/       Command 与 Undo/Redo
|-- stores/        Pinia 编辑会话状态
|-- schema/        Design/Page/Element 类型与运行时校验
|-- renderer/      编辑、预览和导出共享渲染语义
`-- commands/      Add/Delete/Move/Resize/UpdateProperty
```

Vue 组件不直接拥有完整页面树。所有持久化变更先形成命令，再修改 Pinia 中的 Schema，Renderer 订阅状态并更新 Fabric 画布。

## MVP 元素注册表

每个注册项定义：

- 稳定 `type`。
- 默认 `ElementSchema`。
- 属性校验规则。
- 属性面板字段。
- Fabric 渲染方法。
- 是否允许商家编辑。
- 可绑定的模板字段类型。

MVP 注册项：

```text
text   标题、价格、日期、联系方式
image  商品图、Logo、背景图
rect   背景块、边框、装饰块
icon   平台内置且授权清晰的图标
```

Button、Card、Input、Form、Table 和 Chart 属于网页或业务应用低代码，不应为了对齐参考资料而加入第一版。

## 状态和历史

Pinia 保存当前设计、活动页面、选中元素、缩放、平移、剪贴板和历史游标。撤销历史不写入后端；后端版本用于跨会话恢复和审计。

Command 必须提供可执行变更与反向变更，至少支持：

```text
AddElement
DeleteElement
MoveElement
ResizeElement
UpdateElementProps
UpdateElementStyle
```

连续拖动在指针释放时合并为一个历史命令，不能为每个鼠标移动事件创建后端版本。

## Renderer

Renderer 根据 Schema 和 ElementRegistry 生成 Fabric 对象。编辑模式可以增加选框、控制点、辅助线等覆盖层；预览和导出不能包含编辑器覆盖层。

同一个 Schema 必须在重新打开、预览和导出时得到等价结果。字体不可用、素材不存在和未知元素类型必须产生明确错误或受控降级。

## AI 边界

后续 AI 流程：

```text
自然语言
  -> 结构化 Patch
  -> 路径白名单
  -> Schema 校验
  -> 应用 Patch
  -> 新设计版本
  -> Renderer
```

AI 不直接生成或执行 Vue 源码，不允许修改租户、权限和素材所有权。

## 开源项目参考方式

截图提到 OpenTiny TinyEngine、TinyEngine Java Backend、vite-vue3-lowcode、lowcode-editor、puck-ai-lowcode 和 react-visual-editor-tutorial。这些项目可以用于研究 Schema、编辑器和后端边界，但不直接 Fork 作为产品底座。

正式复用任何代码前必须单独确认仓库现状、许可证、维护状态、依赖安全和与本项目 Vue/Java 架构的兼容性。参考架构思想不等同于复制实现或视觉资源。

