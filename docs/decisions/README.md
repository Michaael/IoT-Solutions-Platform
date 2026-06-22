# Architecture Decision Records (ADR)

Архитектурные решения ISPF в формате ADR. Новые решения platform — отдельный файл с монотонным номером.

## Статусы

| Статус | Значение |
|--------|----------|
| **Accepted** | Действует; код и docs должны соответствовать |
| **Proposed** | На обсуждении; не обязательно реализовано |
| **Superseded** | Заменено другим ADR |

## Индекс

| ADR | Тема | Статус |
|-----|------|--------|
| [0008](0008-app-platform-boundary.md) | Граница platform vs solution | Accepted |
| [0009](0009-dogfooding-gate.md) | Gate обобщения перед PR в platform | Accepted |
| [0010](0010-commercial-bundle-licensing.md) | RSA-лицензирование commercial bundle | Accepted |
| [0011](0011-ai-artifact-generation-gates.md) | AI artifact generation + validation gates | Accepted |

## Шаблон нового ADR

```markdown
# ADR-NNNN: Краткий заголовок

Статус: **Proposed** | **Accepted**  
Дата: YYYY-MM-DD

## Контекст

Какая проблема или потребность.

## Решение

Что принято.

## Последствия

Плюсы, минусы, что нужно изменить в коде/docs.

## Связанные материалы

- ссылки
```
