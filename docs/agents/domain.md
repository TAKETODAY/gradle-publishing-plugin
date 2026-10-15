# 领域文档

本仓库采用 single-context 布局，工程技能按以下规则读取领域文档。

## 探索前读取

- 根目录的 `GLOSSARY.md`。
- `docs/adr/` 中与当前工作相关的架构决策记录。
- 如果以后出现根目录 `GLOSSARY-MAP.md`，优先读取该索引，
  再读取相关上下文的术语表，并检查 `src/<context>/docs/adr/`。

上述文件不存在时，静默继续，不报告缺失，也不提前建议创建。
`domain-modeling` 会在术语或决策实际确定时按需创建；
`grill-with-docs` 和 `improve-codebase-architecture` 可调用该技能。

## 文档布局

- `GLOSSARY.md`：仓库统一领域术语。
- `docs/adr/`：架构决策记录，使用编号文件名，例如 `0001-publishing-workflow.md`。

## 使用术语表中的词汇

在工单标题、重构提案、假设和测试名称中引用领域概念时，
使用术语表定义的名称，避免使用其明确排除的同义词。

概念尚未收录时，先判断是否引入了项目未使用的语言；
若确有术语缺口，记录为后续 `domain-modeling` 的输入。

## 显式指出 ADR 冲突

建议与已有 ADR 冲突时，明确指出对应记录和重新讨论的理由，
不要静默覆盖既有决策。
