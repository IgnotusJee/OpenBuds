# REAREye UI 实现分析

分析对象：[killerprojecte/REAREye](https://github.com/killerprojecte/REAREye)

本次参考的是 upstream 仓库 `https://github.com/killerprojecte/REAREye` 和本地克隆 `C:\Users\Ignotus\AppData\Local\Temp\REAREye`，提交短哈希 `b8baeb0`。项目 UI 基于 Android Jetpack Compose，主要使用 Miuix 组件、`dev.chrisbanes.haze` 毛玻璃，以及 `com.kyant.backdrop` 的 backdrop/lens/blur/vibrancy 效果。真机验证使用 adb：`C:\Software\platform-tools\adb.exe`。

## 关键源码位置

- 主入口和主页面切换：`app/src/main/java/hk/uwu/reareye/ui/MainActivity.kt`
- 导航栏总控：`app/src/main/java/hk/uwu/reareye/ui/components/navigation/RearNavigationBar.kt`
- 浮动/玻璃底栏：`app/src/main/java/hk/uwu/reareye/ui/components/navigation/FloatingBottomBar.kt`
- 拖动动画控制：`app/src/main/java/hk/uwu/reareye/ui/components/navigation/DampedDragAnimation.kt`
- 导航 quick action 配置：`app/src/main/java/hk/uwu/reareye/ui/components/navigation/NavigationQuickActionConfig.kt`
- 配置页路由：`app/src/main/java/hk/uwu/reareye/ui/screen/ConfigScreen.kt`
- 关于页：`app/src/main/java/hk/uwu/reareye/ui/screen/AboutScreen.kt`
- 通用卡片：`app/src/main/java/hk/uwu/reareye/ui/components/card/Card.kt`
- 管理器卡片：`app/src/main/java/hk/uwu/reareye/ui/components/card/ManagerModuleStyleCard.kt`
- 毛玻璃封装：`app/src/main/java/hk/uwu/reareye/ui/theme/Acrylic.kt`
- 动态背景 shader：`app/src/main/java/hk/uwu/reareye/utils/effect/BgEffectBackground.kt`
- 通用入场/可见性动画：`app/src/main/java/hk/uwu/reareye/ui/components/motion/ArtMotion.kt`

## 1. 总体 UI 架构

`MainActivity` 用 Compose `setContent` 构建整个应用。主状态包括：

- `themeModeValue`：主题模式。
- `navigationBarModeValue`：底部导航栏模式。
- `currentScreen`：当前主页面 route，默认 `"home"`。
- `navBarVisible`：导航栏是否显示。
- `configInAppListMode`：配置页是否进入全屏 overlay 子界面。
- `pendingConfigQuickManagerTarget`：从导航 quick action 进入配置管理器时的待处理目标。
- `pendingQuickActionTransition`：quick action 跳转配置页时是否跳过主页面过渡动画。
- `navigationQuickActionIds`：用户启用的 quick action 列表。

主界面外层是 `AppTheme` 和 Miuix `Scaffold`，内容区域是一个 `Box`。如果导航栏模式是 `FLOATING_GLASS`，内容区域额外挂 `Modifier.layerBackdrop(backdrop)`，让底部玻璃栏可以采样背后的页面内容。

主页面由 `AnimatedContent(targetState = currentScreen)` 切换，具体页面包括：

- `"home"` -> `HomeScreen`
- `"store"` -> `RearStoreScreen`
- `"config"` -> `ConfigScreen`
- `"about"` -> `AboutScreen`

底部导航栏独立浮在 `Box` 底部，通过 `ArtVisibilityMotion` 控制显隐。导航栏高度会用 `onGloballyPositioned` 记录到 `stableBottomInset`，再传给各页面作为底部 padding，避免内容被底栏遮住。

## 2. 导航栏实现

### 2.1 模式配置

导航栏模式定义在 `ModuleConfig.kt`：

```kotlin
enum class ModuleNavigationBarMode {
    NORMAL,
    FLOATING,
    FLOATING_GLASS,
    SEMI_TRANSPARENT
}
```

对应配置键是：

```kotlin
ConfigKeys.MODULE_NAVIGATION_BAR_MODE
```

设置页中该项是 `ConfigType.EnumSingleSelect`。当用户修改后，`ConfigScreen` 通过 `onNavigationBarModeChange` 通知 `MainActivity` 更新 `navigationBarModeValue`，随后 Compose 重组，`RearNavigationBar` 根据新模式渲染不同样式。

### 2.2 普通和半透明导航栏

在 `RearNavigationBar` 中：

```kotlin
if (navigationBarMode == ModuleNavigationBarMode.NORMAL ||
    navigationBarMode == ModuleNavigationBarMode.SEMI_TRANSPARENT
) {
    NavigationBar(...)
}
```

`NORMAL` 使用 Miuix `NavigationBar`，背景色是 `MiuixTheme.colorScheme.surface`。

`SEMI_TRANSPARENT` 使用透明背景，并额外套一层：

```kotlin
BlurredBar(backdrop = enable, bar)
```

所以半透明模式不是自定义浮动胶囊，而是传统底栏加模糊背景。

### 2.3 浮动和液体玻璃导航栏

`FLOATING` 和 `FLOATING_GLASS` 都走 `FloatingBottomBar`：

```kotlin
val enableGlass = navigationBarMode == ModuleNavigationBarMode.FLOATING_GLASS

FloatingBottomBar(
    selectedIndex = selectedIndex,
    isBlurEnabled = enableGlass,
    ...
)
```

区别是：

- `FLOATING`：使用同样的浮动胶囊布局，但 `isBlurEnabled = false`，不启用 backdrop/lens/blur。
- `FLOATING_GLASS`：`isBlurEnabled = true`，启用完整玻璃效果。

玻璃效果主要在 `FloatingBottomBar.kt` 的 `drawBackdrop` 中：

```kotlin
drawBackdrop(
    backdrop = backdrop,
    shape = { ContinuousCapsule },
    effects = {
        vibrancy()
        blur(8f.dp.toPx())
        lens(24f.dp.toPx(), 24f.dp.toPx())
    },
    highlight = { Highlight.Default },
    shadow = { Shadow.Default },
    onDrawSurface = { drawRect(containerColor) },
)
```

选中胶囊还有第二层 `drawBackdrop`，并使用 `rememberCombinedBackdrop(backdrop, tabsBackdrop)` 叠加背景采样。拖动或按压时通过 `pressProgress` 放大 lens、highlight、shadow 和 innerShadow。

液体感主要来自几部分叠加：

- `ContinuousCapsule` 胶囊形状。
- `blur()` 背景模糊。
- `vibrancy()` 提升背景色彩。
- `lens()` 模拟折射/透镜。
- `Highlight` 增加高光。
- `Shadow` 和 `InnerShadow` 增加厚度。
- 按压时 `scaleX`、`scaleY`、`velocity` 拉伸选中胶囊。

### 2.4 选择逻辑

导航栏选择由 `MainActivity.currentScreen` 单一状态驱动。

`RearNavigationBar` 根据当前 route 计算：

```kotlin
val selectedIndex = items.indexOfFirst { it.route == currentScreen }.coerceAtLeast(0)
```

点击普通 tab 时：

```kotlin
onScreenSelected(items[index].route)
```

回到 `MainActivity`：

```kotlin
onScreenSelected = { currentScreen = it }
```

所以页面选中状态、底栏选中胶囊位置、`AnimatedContent` 页面内容全部由 `currentScreen` 推导。

### 2.5 拖动选择逻辑

浮动导航栏的拖动选择由 `DampedDragAnimation` 实现。

`FloatingBottomBar` 内部维护：

```kotlin
var currentIndex by remember { mutableIntStateOf(selectedIndex) }
val dampedDragAnimation = remember(...) {
    DampedDragAnimation(
        initialValue = selectedIndex.toFloat(),
        valueRange = 0f..(tabsCount - 1).toFloat(),
        pressedScale = 78f / 56f,
        ...
    )
}
```

拖动时：

```kotlin
updateValue(
    (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
        .coerceIn(0f, tabsCount - 1f)
)
```

也就是把横向拖动距离除以单个 tab 宽度，转换成浮点 index。松手时：

```kotlin
val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
currentIndex = targetIndex
animateToValue(targetIndex.toFloat())
```

再由：

```kotlin
LaunchedEffect(currentIndex) {
    if (currentIndex != selectedIndex) {
        onSelected(currentIndex)
    }
}
```

通知外层切换页面。

`DampedDragAnimation` 本身负责：

- 手势开始时 `press()`，把 `pressProgress` 动画到 1。
- 手势结束时 `release()`，把 `pressProgress` 和缩放还原。
- `updateValue()` 用 spring 把选中胶囊动画到目标浮点位置。
- `updateVelocity()` 记录速度，用于拖动时拉伸玻璃胶囊。
- `canDrag()` 限制触点不能拖出底栏有效区域。

### 2.6 Quick action 长按拖选

配置 tab 支持 quick action，默认启用：

```kotlin
DefaultNavigationQuickActionIds = listOf(
    NavigationQuickActionComponentManagerId,
    NavigationQuickActionCardManagerId,
)
```

交互由 `navigationQuickActionDragGesture` 实现：

1. `awaitFirstDown` 捕获按下。
2. 等待半个 long press timeout。
3. 触发后调用 `onOpenQuickActions` 弹出菜单。
4. 持续追踪手指坐标。
5. `NavigationQuickMenuController.updateHover()` 根据指针位置判断 hover 到哪个 quick action。
6. 松手时，如果 hover 到 action，就执行 `onQuickActionSelected(action.target)`；如果 hover 到编辑目标，就打开 quick action 编辑器。

Quick action 菜单本身是 `Popup`，使用 `Animatable progress` 做弹出 alpha、scale 和 y 位移动画。

Quick action 的按钮在 `FLOATING_GLASS` 模式下也使用 `drawBackdrop + blur + lens + vibrancy`，hover 时会放大、加深边框和高光。

### 2.7 Quick action 编辑器

编辑器是 `OverlayBottomSheet`，内部把 quick action 分为 active 和 available 两个区域。

拖动排序由 `NavigationQuickEditorDropList` 实现：

- `activeBounds` 和 `availableBounds` 记录各行坐标。
- `dragState` 记录拖动项、来源区域、目标区域、插入位置。
- 拖动 active 项可重排。
- 从 available 拖到 active 可新增，最多 `MaxNavigationQuickActions = 5`。
- 从 active 拖回 available 可移除。

保存后调用：

```kotlin
onQuickActionIdsChanged(nextIds)
```

`MainActivity` 会规范化并写入 `MODULE_NAVIGATION_QUICK_ACTIONS`。

## 3. 页面切换过渡动画

### 3.1 主页面切换动画

主页面切换写在 `MainActivity.kt` 的 `AnimatedContent`：

```kotlin
AnimatedContent(
    targetState = currentScreen,
    contentKey = { it },
    transitionSpec = { ... },
    label = "ScreenTransition",
)
```

它不是为每个页面单独写动画，而是统一根据页面顺序计算方向：

```kotlin
private val MainScreenOrder = listOf("home", "store", "config", "about")

val initialIndex = MainScreenOrder.indexOf(initialState).coerceAtLeast(0)
val targetIndex = MainScreenOrder.indexOf(targetState).coerceAtLeast(0)
val forward = targetIndex >= initialIndex
```

也就是说：

- 从 `home` 到 `store/config/about` 都算 forward。
- 从 `about` 到 `config/store/home` 都算 backward。
- 同 index 或未知 route 被兜底为 forward。

进入动画：

```kotlin
fadeIn(
    animationSpec = tween(
        durationMillis = 210,
        delayMillis = 50,
        easing = LinearOutSlowInEasing,
    )
) + slideInHorizontally(
    animationSpec = tween(
        durationMillis = 280,
        easing = FastOutSlowInEasing,
    )
) { fullWidth ->
    if (forward) fullWidth / 9 else -fullWidth / 9
}
```

退出动画：

```kotlin
fadeOut(
    animationSpec = tween(
        durationMillis = 110,
        easing = FastOutLinearInEasing,
    )
) + slideOutHorizontally(
    animationSpec = tween(
        durationMillis = 190,
        easing = FastOutLinearInEasing,
    )
) { fullWidth ->
    if (forward) -fullWidth / 12 else fullWidth / 12
}
```

组合方式是：

```kotlin
enterTransition togetherWith exitTransition
```

这个配置有几个明显特点：

- 进入动画比退出动画长，视觉重点放在新页面。
- 进入 fade 有 50ms delay，让旧页面先开始离场。
- 位移很小，只移动屏宽的 `1/9` 或 `1/12`，所以是轻量横向滑动，不是整屏推入。
- `FastOutSlowInEasing` 用于进入，手感是先快后慢。
- `FastOutLinearInEasing` 用于退出，旧页面快速淡出，减少拖泥带水。

### 3.2 Quick action 跳配置页时跳过动画

主页面切换有一个特例：

```kotlin
if (pendingQuickActionTransition && targetState == "config") {
    pendingQuickActionTransition = false
    return@AnimatedContent ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = ExitTransition.None,
    )
}
```

当用户从导航栏 quick action 直接进入某个配置管理器时，代码会设置：

```kotlin
pendingConfigQuickManagerTarget = target.managerType
pendingQuickActionTransition = true
configInAppListMode = true
currentScreen = "config"
```

这样做的目的不是没有动画，而是避免两段动画叠加：

1. 主页面先横向切到 `config`。
2. 配置页再进入 overlay 管理器。

如果两段都播，会显得跳转路径太长。这里直接把主页面切换设为 `None`，让用户感觉是从 quick action 直达目标管理页。

### 3.3 配置页内部路由动画

`ConfigScreen` 内部也有自己的路由栈：

```kotlin
var routeStack by remember {
    mutableStateOf(listOf(ConfigRoute.Root, managerRoute(quickManagerTarget)).filterNotNull())
}
```

当前 route：

```kotlin
val currentRoute = routeStack.last()
val animatedRoute = ConfigAnimatedRoute(
    route = currentRoute,
    depth = routeStack.size,
)
```

配置页内部同样使用 `AnimatedContent`：

```kotlin
AnimatedContent(
    targetState = animatedRoute,
    contentKey = { it.route },
    transitionSpec = { ... },
    label = "ConfigRouteTransition",
)
```

方向判断不是根据 route 顺序，而是根据栈深度：

```kotlin
val forward = targetState.depth >= initialState.depth
```

含义：

- 进入分类、收藏、AppList、管理器时，`routeStack` 变长，forward。
- 返回时，`routeStack` 变短，backward。

具体动画参数和主页面完全一致：进入 `fadeIn 210ms delay 50ms + slideIn 280ms`，退出 `fadeOut 110ms + slideOut 190ms`。这让主页面和配置子页面保持统一动效语言。

### 3.4 Overlay 子界面与导航栏显隐配合

配置页有一类 route 被视为 overlay：

```kotlin
ConfigRoute.AppList
ConfigRoute.RearWallpaperManager
ConfigRoute.BusinessManager
ConfigRoute.SceneRouteManager
ConfigRoute.CardManager
ConfigRoute.BusinessExtraManager
ConfigRoute.CustomBoundsCompatManager
```

进入 overlay 时：

```kotlin
fun openOverlayRoute(route: ConfigRoute) {
    routeScope.launch {
        onAppListModeChange(true)
        delay(NAV_BAR_EXIT_DURATION_MS)
        routeStack = routeStack + route
    }
}
```

`NAV_BAR_EXIT_DURATION_MS = 220L`。也就是说先通知 `MainActivity` 隐藏底部导航栏，等底栏退出动画播一部分后，再切换到 overlay 内容。

退出 overlay 时：

```kotlin
fun closeOverlayRoute() {
    routeScope.launch {
        routeStack = listOf(ConfigRoute.Root)
        delay(OVERLAY_ROUTE_EXIT_DURATION_MS)
        onAppListModeChange(false)
    }
}
```

`OVERLAY_ROUTE_EXIT_DURATION_MS = 220L`。也就是先让 overlay 内容退回根页面，再延迟恢复底部导航栏。

这个设计避免底栏和全屏管理器同时抢视觉焦点。

### 3.5 关于页内部路由动画

关于页内部也有一个轻量路由：

```kotlin
private sealed interface AboutRoute {
    data object Root
    data object Contributors
    data object Licenses
}
```

它包装成：

```kotlin
AboutAnimatedRoute(
    route = route,
    depth = if (route is AboutRoute.Root) 0 else 1,
)
```

关于页的 `AnimatedContent` 使用同一套动画参数，方向判断也是：

```kotlin
val forward = targetState.depth >= initialState.depth
```

所以：

- Root -> Contributors/Licenses：向前小幅右侧滑入。
- Contributors/Licenses -> Root：向后小幅左侧滑入。

返回由 `BackHandler` 处理：

```kotlin
BackHandler(enabled = route is AboutRoute.Contributors || route is AboutRoute.Licenses) {
    route = AboutRoute.Root
}
```

### 3.6 模板配置页复用动画

`TemplateConfigRouteTransition.kt` 把同一套过渡抽成泛型组件：

```kotlin
fun <T : Any> TemplateConfigRouteTransition(
    target: T?,
    templateContent: @Composable (T) -> Unit,
    content: @Composable () -> Unit,
)
```

方向判断更简单：

```kotlin
val forward = targetState != null
```

含义：

- `target == null`：显示普通内容。
- `target != null`：进入模板配置内容，forward。
- 从模板配置返回普通内容，backward。

该组件被 `CardManagerScreen` 和 `RearWallpaperManagerScreen` 使用，用于局部编辑页/模板页切换。

### 3.7 导航栏显隐动画

底部导航栏显隐不是 `AnimatedContent`，而是 `ArtVisibilityMotion`：

```kotlin
ArtVisibilityMotion(
    visible = showNavigation,
    enterAlphaDurationMillis = 260,
    enterTransformDurationMillis = 380,
    exitAlphaDurationMillis = 180,
    exitTransformDurationMillis = 240,
    hiddenEnterScale = 1f,
    hiddenExitScale = 1f,
    slideDivisor = 3,
    hiddenOffsetFallback = 28.dp,
)
```

`showNavigation` 的条件是：

```kotlin
navBarVisible && !(currentScreen == "config" && configInAppListMode)
```

也就是说只有配置页进入 AppList/管理器 overlay 时隐藏底栏。

`ArtVisibilityMotion` 的实现思路：

- `Hidden`：透明，向下偏移。
- `Primed`：进入前准备状态。
- `Visible`：alpha 到 1，translationY 到 0。
- 使用 `MutableStyleState` 和 Compose Style API 驱动 alpha、scale、translationY。

导航栏阴影还有单独的 `animateFloatAsState`：

```kotlin
targetValue = if (showNavigation) 1f else 0f
durationMillis = if (showNavigation) 380 else 240
```

这个值传入 `RearNavigationBar`，最终影响浮动栏 shadow alpha。

### 3.8 列表项和卡片入场动画

项目还大量使用 `ArtRevealItem` 和 `ArtStaggeredReveal` 做局部内容入场。

`ArtRevealItem` 是单项入场：

- alpha：240ms。
- transform：360ms。
- 初始 scale：0.985。
- 垂直偏移：内容高度 / 14。

`ArtStaggeredReveal` 是交错入场：

- alpha：220ms。
- transform：320ms。
- 初始 scale：0.988。
- 垂直偏移：内容高度 / 18。
- 可传 `delayMillis` 做列表项错峰出现。

关于页卡片使用 `AboutReveal` 包装，实际调用 `ArtStaggeredReveal`。贡献者列表、商店页、多个管理器空状态也使用 `ArtRevealItem`。

### 3.9 动画配置总结

REAREye 的过渡动画风格可以概括为：

- 页面级切换统一使用 `AnimatedContent`。
- 方向来自“页面顺序”或“路由深度”，不是硬编码每个页面。
- 页面位移幅度很小，进入 `1/9` 屏宽，退出 `1/12` 屏宽。
- 新页面进入更慢、更完整，旧页面退出更快。
- overlay 管理器会先隐藏底栏，再切换内容。
- quick action 直达管理器时跳过主页面切换动画。
- 列表/卡片入场用独立的 `ArtVisibilityMotion` 系列，做轻量淡入、上移、缩放。

如果要复刻这种动效，建议抽一个统一函数：

```kotlin
fun horizontalDepthTransform(forward: Boolean): ContentTransform {
    return fadeIn(
        tween(210, delayMillis = 50, easing = LinearOutSlowInEasing)
    ) + slideInHorizontally(
        tween(280, easing = FastOutSlowInEasing)
    ) { if (forward) it / 9 else -it / 9 } togetherWith
    fadeOut(
        tween(110, easing = FastOutLinearInEasing)
    ) + slideOutHorizontally(
        tween(190, easing = FastOutLinearInEasing)
    ) { if (forward) -it / 12 else it / 12 }
}
```

然后主页面用 route order 算 `forward`，子页面用 stack depth 算 `forward`。

## 4. 子界面的卡片 UI

### 4.1 SuperCard

`SuperCard` 是项目最基础的业务卡片封装：

```kotlin
BasicComponent(
    modifier = modifier.animateContentSize(...),
    title = title,
    summary = summary,
    startAction = startAction,
    endActions = endActions,
    bottomAction = bottomAction,
    onClick = onClick,
)
```

它没有直接画卡片背景，而是把内容封装为 Miuix `BasicComponent`。外部可以把它放进 Miuix `Card` 或透明玻璃 `Card` 中。

### 4.2 管理器卡片

`ModuleStyleManagerCard` 是管理页面常用卡片：

- 外层 `Card`
- 内边距 `PaddingValues(16.dp)`
- 标题 `17.sp`，`FontWeight(550)`
- 摘要 `12.sp`
- 可选 badge group
- 可选 trailing 区域
- 可选底部操作区
- 操作区上方有 `HorizontalDivider`
- 左右操作由 `leftAction` 和 `rightAction` 注入

这使得业务、卡片、场景路由、壁纸等管理器可以复用统一卡片结构。

### 4.3 子界面路由体验

配置页内部有两类界面：

1. 普通层级：根配置、分类、收藏。
2. Overlay 管理器：AppList、壁纸管理、业务管理、卡片管理、场景路由管理等。

普通层级保留顶部栏和底部导航栏，使用 routeStack 深度切换。

Overlay 管理器会隐藏底部导航栏，顶部栏通常自己提供返回按钮和毛玻璃效果。这让复杂管理器看起来像独立全屏页面。

### 4.4 毛玻璃顶部栏

毛玻璃封装在 `Acrylic.kt`：

```kotlin
fun Modifier.rearAcrylicEffect(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    blurRadius: Dp = 24.dp,
)

fun Modifier.rearAcrylicSource(hazeState: HazeState)
```

用法一般是：

- 滚动内容加 `.rearAcrylicSource(hazeState)`。
- `TopAppBar` 加 `.rearAcrylicEffect(hazeState, hazeStyle)`。

`HazeStyle` 根据当前 surface 亮度决定 tint alpha：暗色主题更透明，亮色主题更不透明。

## 5. 关于页渐变和背景效果

### 5.1 顶部 logo 区随滚动渐隐

关于页定义：

```kotlin
private val AboutGradientFadeDistance = 389.dp
```

滚动进度：

```kotlin
val scrollProgress =
    if (firstVisibleItemIndex > 0) 1f
    else firstVisibleItemScrollOffset / AboutGradientFadeDistancePx
```

这个值同时控制：

- TopAppBar 背景 alpha。
- TopAppBar 标题 alpha。
- 动态背景 alpha。

TopAppBar 是越滚越实：

```kotlin
color = surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f)
titleColor = onSurface.copy(alpha = scrollProgress)
```

动态背景是越滚越淡：

```kotlin
alpha = { 1f - scrollProgress }
```

### 5.2 logo、标题、版本号分阶段消失

关于页记录：

- `logoAreaY`
- `iconY`
- `projectNameY`
- `versionCodeY`

滚动时按距离切三段：

- 版本号先消失。
- 应用名称后消失。
- 图标最后消失。

每个元素用：

```kotlin
graphicsLayer {
    alpha = 1 - progress
    scaleX = 1 - progress * 0.05f
    scaleY = 1 - progress * 0.05f
}
```

所以不是简单整体 fade，而是分层、分阶段、轻微缩放的渐隐。

### 5.3 Runtime shader 背景

关于页背景使用：

```kotlin
BgEffectBackground(
    dynamicBackground = runtimeShaderSupported,
    effectBackground = runtimeShaderSupported,
    alpha = { 1f - scrollProgress },
)
```

`BgEffectBackground` 内部先判断：

```kotlin
val shaderSupported = remember { isRuntimeShaderSupported() }
if (!shaderSupported) {
    Box(modifier = modifier.background(surface))
    return
}
```

支持时才创建 `BgEffectPainter`，绘制 AGSL runtime shader。shader 用四个彩色点、Perlin noise、色相/饱和度调整和 grain noise 形成动态渐变背景。

### 5.4 关于页玻璃卡片

关于页卡片外层是透明 `Card`，背景靠 `textureBlur`：

```kotlin
Card(
    modifier = Modifier.textureBlur(
        backdrop = backdrop,
        shape = SmoothRoundedCornerShape(16.dp),
        blurRadius = 60f,
        noiseCoefficient = 0.001f,
        colors = BlurColors(blendColors = visualTokens.cardBlendColors),
        enabled = true,
    ),
    colors = CardDefaults.defaultColors(Color.Transparent, Color.Transparent),
) {
    SuperCard(...)
}
```

暗色和亮色主题下使用不同 `BlendColorEntry`，从而让卡片在不同背景上保持可读性。

## 6. 特效开启和关闭

项目里没有一个“关闭所有 UI 特效”的总开关。实际控制点分散在几层。

### 6.1 导航栏玻璃特效

用户通过 `MODULE_NAVIGATION_BAR_MODE` 控制：

- `NORMAL`：普通底栏。
- `SEMI_TRANSPARENT`：透明底栏 + blur。
- `FLOATING`：浮动胶囊，无玻璃。
- `FLOATING_GLASS`：浮动胶囊 + backdrop/lens/blur/vibrancy。

核心判断：

```kotlin
val enableGlass = navigationBarMode == ModuleNavigationBarMode.FLOATING_GLASS
FloatingBottomBar(isBlurEnabled = enableGlass)
```

### 6.2 Runtime shader 能力兜底

动态背景不是用户开关，而是能力判断：

```kotlin
isRuntimeShaderSupported()
```

不支持时自动退回纯色背景。

### 6.3 局部 enabled 参数

部分效果有局部 `enabled` 或布尔参数：

- `FloatingBottomBar(isBlurEnabled = enableGlass)`
- `BgEffectBackground(effectBackground = runtimeShaderSupported)`
- `textureBlur(enabled = true)`
- `rearAcrylicEffect(...)` 默认直接启用

如果要给 OpenBuds 复刻并增加“特效开关”，建议加一个总开关：

```kotlin
val enableUiEffects = userPrefs.enableUiEffects && runtimeShaderSupported
```

然后统一传给：

- `FloatingBottomBar(isBlurEnabled = enableUiEffects && mode == FLOATING_GLASS)`
- `BgEffectBackground(effectBackground = enableUiEffects)`
- `textureBlur(enabled = enableUiEffects)`
- 顶部栏在关闭时退回普通半透明/纯色背景

### 6.4 Backdrop 拓扑约束

REAREye 的 kyant backdrop 和 Miuix texture blur 没有共用一个全局内容 source：

- 主页面 `FLOATING_GLASS` 只在内容层挂 kyant `Modifier.layerBackdrop(backdrop)`，供底栏 `drawBackdrop`、tabs backdrop 和 `rememberCombinedBackdrop` 使用。
- Miuix `textureBlur` 的 source 是局部背景层，例如 About 页的背景绘制层；`textureBlur` 卡片是前景 consumer，不会被同一个 source 的 `drawContent()` 捕获。
- 半透明底栏单独调用 `rememberBlurBackdrop(true)`，source 只服务底栏，不向页面卡片传播。

因此复刻时不能把 Miuix `textureLayerBackdrop` 挂在包含 `AnimatedContent`、卡片、底栏和 `textureBlur` consumer 的全局父节点上；否则 source 捕获自己的 consumer，可能在 native blur 准备渲染树时形成递归。

## 7. 可复刻的实现建议

如果要在当前项目复刻 REAREye 的 UI 体验，可以按这个拆分：

1. 先实现 route 状态和统一 `AnimatedContent` 过渡。
2. 再实现普通底栏和浮动底栏模式切换。
3. 再做浮动底栏的选中胶囊拖动，核心是浮点 index + 松手 round。
4. 最后加玻璃层：backdrop、blur、lens、highlight、shadow。
5. 子页面统一使用 routeStack，普通层级保留底栏，overlay 层级隐藏底栏。
6. 卡片先使用普通 `Card + BasicComponent`，再为高价值页面叠加 `textureBlur`。
7. 关于页可以先做 `scrollProgress` 控制 TopAppBar/背景 alpha，再补 runtime shader 和分阶段 logo 渐隐。

关键不是单个特效，而是“状态驱动 + 统一动画参数 + 分层特效开关”。REAREye 的代码基本没有把动画写死在点击回调里，而是让状态变化触发 Compose 动画，这一点是最值得复用的。

## 8. OpenBuds 当前落地状态

OpenBuds 现在已经把 REAREye 的全特效链路收敛成“模式 + 用户开关”两层，没有设备黑名单，也没有 `safe surfaces active` 这类强制回退语义。`UiRenderCapabilities` 只负责把底栏模式和总开关映射成具体的渲染开关；真正的 blur / haze 能力只在底层组件创建时做 runtime 支持判断。

### 8.1 全局能力层

实现入口为 `app/src/main/java/dev/ignotus/openbuds/ui/UiEffectsPolicy.kt`：

- `UiRenderCapabilities` 只接受 `mode` 和 `userEffectsEnabled`。
- 派生值只包含 `effectsEnabled`、`floatingBottomBarEnabled`、`semiTransparentBottomBar`、`rootBackdropEnabled`、`navigationBackdropEnabled`、`liquidGlassEnabled`、`glassCardsEnabled`、`backgroundGradientEnabled`。
- 不再暴露 `deviceEffectsSupported` / `effectsBlockedByDevice`，也不再有 Xiaomi / Redmi / Poco 黑名单。
- `rememberTextureBackdrop()`、`BlurredBar()` 和 About 的 acrylic helper 只在底层调用 `isRenderEffectSupported()`，这是库级 runtime guard，不是 app 级安全模式。
- `rememberTextureBackdrop()` 只创建 Miuix `LayerBackdrop`，不再通过 `drawContent()` 捕获整棵页面内容；具体 source 由 source-only 背景层提供。
- 局部玻璃色彩读取当前 `MaterialTheme.colorScheme`；Material 与 MIUIX-like 模式共享同一套 active color scheme，不直接从 `MiuixTheme` 取 tint。

### 8.2 Backdrop 边界

当前渲染边界与 REAREye 对齐：

- `FloatingGlass` 保留 kyant backdrop 的 root / tabs / combined backdrop 链，并继续使用 blur / lens / vibrancy。
- `SemiTransparent` 改成 blur-backed bar，走 `BlurredBar()` + `textureBlur`，但只影响底栏。
- `GlassCard` 只在 `FloatingGlass` + `UI effects` 时走 `textureBlur`；About 顶部 logo / 顶栏的 acrylic / haze 只跟随 `UI effects` 和 runtime render-effect 支持，不再依赖 `glassCardsEnabled`。
- 普通渐变背景只作为视觉层存在，不参与设备策略判断。
- `Normal`、`Floating` 不创建液态玻璃 root backdrop；`SemiTransparent` 只创建底栏局部 `textureBackdrop`，且不会通过 `LocalTextureBackdrop` 暴露给卡片；`FloatingGlass` 同时拥有页面 Miuix `textureBackdrop` 和导航栏 kyant `backdrop`。
- 页面 Miuix `textureBackdrop` 只挂在 sibling background source layer 上，该 layer 只绘制背景色/渐变；前景 `AnimatedContent`、`GlassCard`、`BlurredBar` 只消费 backdrop，不进入 source 的内容捕获范围。

### 8.3 Settings / About 清理

Settings 和 About 根页面已去掉旧的安全模式文案：

- 不再显示 `blocked`、`safe`、`blocked on this device` 之类的提示。
- Appearance / About 只保留 on/off、模式和真实状态项。
- About 头部、卡片、图标区域统一走和 REAREye 一样的 blur primitive，但 About 顶部 acrylic / haze 仅受 `effectsEnabled` 控制；卡片玻璃仍受 `glassCardsEnabled` 控制；Semi transparent 模式回到普通 Compose surface。
- Settings 的二级路由栈保存在 theme style 条件包装之外，避免 Material / MIUIX 切换时 `MiuixTheme` subtree 替换导致 Appearance 等子页面被重置回根页。
- 底栏显隐状态仍由 Settings overlay 进入/退出流程驱动，但主题切换不能改变当前子路由，也不能让底栏停留在错误隐藏态。

### 8.4 系统栏和冷启动

当前项目在 `MainActivity` 的 `setContent` 前完成 edge-to-edge 和透明系统栏初始化，同时 XML theme 也声明透明 status/navigation bar 和关闭系统栏 contrast enforcement。这样冷启动首帧就进入透明系统栏状态，避免等到 Compose `SideEffect` 执行后才出现玻璃/透明效果。

### 8.5 验证状态

- `UiEffectsPolicyTest` 已覆盖 Normal / SemiTransparent / Floating / FloatingGlass 的 effects on/off 矩阵，并额外断言 `SemiTransparent` 只启用底栏 surface，不启用 root backdrop、navigation backdrop、liquid glass 或 glass cards。
- UI smoke test 已覆盖 `FloatingGlass + Miuix + UI effects` 启动渲染，避免回退到只测默认 `Floating + effects off`。
- UI smoke test 目录下已增加 theme style 切换回归用例，用于约束 Settings > Appearance 中切换 Material / MIUIX 不应返回根页。
- `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin` 已通过。
- 已在 Xiaomi 2210132C / Android 16 / HyperOS OS3.0 上保留 DataStore `FloatingGlass + Miuix + effectsEnabled=true` 安装启动验证；清空 crash buffer 后启动，进程保持运行，UI tree 正常显示 Home，`logcat -b crash -d` 没有新的 `SIGSEGV`、`RenderThread`、`MiBackgroundBlurBlend`。

### 8.6 液态玻璃 `DampedDragAnimation` 稳定性修复

OpenBuds 的 `FloatingLiquidNavigationBar` 在 `remember` `DampedDragAnimation` 时额外将 `tabWidthPx` 和 `totalWidthPx` 放入 key 中。这两个值在首次 layout 测量时从 0 变为实际像素值，导致 `DampedDragAnimation` 被整体重建（所有内部 `Animatable` 重新初始化），造成切换 tab 时动画不稳定。

REAREye 的做法是：
- `tabWidthPx` 和 `totalWidthPx` 均使用 `mutableFloatStateOf` 声明为 state-backed 变量。
- 两个值在 `onGloballyPositioned` 回调中同时赋值（而非通过派生 `val` 计算 `tabWidthPx`）。
- `remember` 的 key 仅为 `(animationScope, tabsCount, density, isLtr)`，不包含会变化一次的 layout 测量值。
- 闭包内读取 `tabWidthPx` 时，因为变量是 `var` + delegate，Kotlin 捕获的是 delegate 引用，始终读到当前值。

修复内容（`OpenBudsApp.kt`）：
- `tabWidthPx` 从 `val = if (...) ... else 0f` 改为 `var ... by remember { mutableFloatStateOf(0f) }`，并在 `onGloballyPositioned` 内与 `totalWidthPx` 一同赋值。
- `remember` key 从 `(animationScope, tabs.size, density, isLtr, tabWidthPx, totalWidthPx)` 改为 `(animationScope, tabs.size, density, isLtr)`，与 REAREye 对齐。
- 移除不再使用的 `kotlinx.coroutines.delay` import。

后续追加修复（同文件）：
- 新增 `canDrag` 约束：通过 `DampedDragAnimationHolder` 持有 animation 引用，`canDrag` 闭包读取当前 `tabWidthPx` 和 `totalWidthPx`（state-backed delegate），限制触点不能拖出底栏有效区域。这与 REAREye `FloatingBottomBar` 的 `canDrag` 等价。
- 新增 kyant `drawBackdrop` 的 `layerBlock`：`FloatingGlass` 模式下，外层胶囊 backdrop 根据 `pressProgress` 施加 `scaleX/scaleY`（`lerp(1f, 1f + 16.dp / size.width, progress)`）。选中的胶囊 tab indicator 的 `layerBlock` 负责 `scaleX/scaleY` 和 velocity 拉伸（`velocity / 10f`，`scaleX /= 1f - velocity*0.75f`, `scaleY *= 1f - velocity*0.25f`），与 REAREye 的层内液变一致。
- `panelOffsetPx`（面板整体微移偏移）传播到 tab indicator `graphicsLayer.translationX` 和 tabs backdrop `graphicsLayer.translationX`，使拖动时选中胶囊和半透明 tab overlay 同步跟随面板偏移。
- `onDragStarted` 和 `onDrag` 回调中，`snapToValue`/`updateValue` 改为基于 `targetValue` 而非当前 `value`，消除快速拖动时因 `Animatable.value` 落后于目标值导致的位置跳变。

### 8.7 根容器 clip 移除

OpenBuds 原先在 `appContent` 根 `Box` 上挂有 `graphicsLayer { clip = true }`。REAREye 不对根容器做裁剪，因为：
- `AnimatedContent` 的 `slideInHorizontally`/`slideOutHorizontally` 使用 `graphicsLayer { translationX }` 实现位移，不需要父级 clip。
- 悬浮底栏的 `shadow` 和 `drawBackdrop` 阴影会略微超出 `Box` 边界；根 clip 可能在部分屏幕宽度上切断阴影边缘。

已移除根 `graphicsLayer { clip = true }`，与 REAREye 对齐。
