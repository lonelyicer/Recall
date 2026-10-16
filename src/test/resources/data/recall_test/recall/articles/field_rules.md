---
id: recall_test:field_rules
title: Field Rules
summary: A compact checklist for the expedition.
tags: rules
---

# Markdown Rendering Test

A comprehensive test document for a Markdown renderer.

**Bold text**, *italic text*, ***bold and italic***, ~~strikethrough~~, and `inline code`.

This paragraph contains a [normal link](https://example.com "Example title"), an <https://example.com> autolink, and an email address: <test@example.com>.

Escaped characters: \*not italic\*, \# not a heading, \[not a link\].

Unicode: 中文、日本語、한국어、English. Emoji: 😀 🚀 🎉 ❤️

---

## 1. Headings

# Heading 1
## Heading 2
### Heading 3
#### Heading 4
##### Heading 5
###### Heading 6

Heading with **bold**, *italic*, and `code`.

Setext-style heading 1
======================

Setext-style heading 2
----------------------

## 2. Paragraphs and Line Breaks

This is the first paragraph.

This is the second paragraph. It is separated by a blank line.

This line ends with two spaces.  
This line should start on a new line.

This line ends with a backslash.\
This line should also start on a new line.

This paragraph contains **bold text with *nested italic text***.

This is `inline code with **Markdown syntax** inside it`.

## 3. Lists

### Unordered Lists

- Apple
- Banana
- Orange
    - Blood orange
    - Mandarin
        - Satsuma
- Grape

### Ordered Lists

1. First item
2. Second item
3. Third item
    1. Nested first item
    2. Nested second item
4. Fourth item

### Alternative List Markers

* Item using an asterisk
* Another item

+ Item using a plus sign
+ Another item

### Lists with Paragraphs

- First item.

  This is a second paragraph within the first item.

- Second item with **bold text**.

    - Nested item
    - Another nested item

### Task Lists (GFM)

- [x] Implement headings
- [x] Implement paragraphs
- [ ] Implement tables
- [ ] Test nested blockquotes

## 4. Blockquotes

> This is a blockquote.
>
> It contains multiple paragraphs.
>
> > This is a nested blockquote.
> >
> > It can also contain **bold text** and `inline code`.
>
> Back to the outer blockquote.

> ### Heading inside a blockquote
>
> - List item one
> - List item two
>
> ```kotlin
> fun main() {
>     println("Code inside a blockquote")
> }
> ```

## 5. Code

Inline code: `val answer = 42`.

Inline code containing backticks: `` `this is surrounded by backticks` ``.

### Fenced Code Block

```
Plain code block.
Special characters: < > & " '
Markdown syntax should not be parsed here: **bold**
```

### Kotlin

```kotlin
data class User(
    val id: Long,
    val name: String,
    val active: Boolean = true,
)

fun greet(user: User): String {
    return "Hello, ${user.name}!"
}

fun main() {
    val user = User(id = 1L, name = "World")
    println(greet(user))
}
```

### Java

```java
public final class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, Markdown!");
    }
}
```

### JavaScript

```javascript
const items = ["apple", "banana", "orange"];

const result = items
    .filter(item => item.length > 5)
    .map(item => item.toUpperCase());

console.log(result);
```

### Indented Code Block

    This is an indented code block.
    It should be rendered as code.
    **This should not be bold.**

## 6. Tables (GFM)

| Feature | Status | Notes |
| :--- | :---: | ---: |
| Headings | ✅ | Supported |
| Lists | ✅ | Nested lists |
| Tables | 🔄 | Testing |
| Code blocks | ✅ | `fenced` |
| Links | ✅ | [Example](https://example.com) |

| Left aligned | Center aligned | Right aligned |
| :--- | :---: | ---: |
| Left | Center | Right |
| A longer cell | Middle | 123.45 |
| **Bold** | *Italic* | ~~Deleted~~ |

## 7. Links and Images

Inline link: [OpenAI](https://openai.com/).

Link with title: [Example](https://example.com "This is a tooltip").

Reference link: [Markdown Guide][guide].

[guide]: https://www.markdownguide.org/ "Markdown Guide"

Reference link with collapsed syntax: [Markdown Guide][].

[Markdown Guide]: https://www.markdownguide.org/

Image with alt text and a hover title (a resource-pack PNG):

![Field route](recall_test:textures/markdown/field_route.png "A local route illustration")

Linked image (click to jump to this section):

[![Field marker](markdown/field_marker)](#7-links-and-images)

The namespace defaults to the article namespace. `textures/` and `.png` may be omitted:

![Vertical route](markdown/vertical_route "A tall image keeps its aspect ratio")

Images also work inside formatting, lists, quotes and table cells:

- **![Field marker](markdown/field_marker)**

> ![Field route](markdown/field_route)

| Left | Center | Right |
| :--- | :---: | ---: |
| ![Marker](markdown/field_marker) | [![Marker](markdown/field_marker)](#7-links-and-images) | ![Marker](markdown/field_marker) |

Missing resource fallback:

![Missing illustration](recall_test:textures/markdown/not_found.png)


## 8. Horizontal Rules

First section.

---

Second section.

***

Third section.

___

## 9. HTML (Optional)

<div>
  <strong>Inline HTML bold</strong>
  <p>A paragraph inside an HTML block.</p>
</div>

<details>
  <summary>Click to expand</summary>

This is content inside a details element.

- An item
- Another item
</details>

## 10. Escaping and Special Characters

Literal asterisks: \* \*\* \*\*\*

Literal brackets: \[ \] \( \)

Literal punctuation: \# \+ \- \. \! \| \_

Backslash: \\

HTML entities: &amp; &lt; &gt; &quot; &apos; &copy; &mdash;

Inline HTML entities: &lt;script&gt;alert("test")&lt;/script&gt;

## 11. GFM Extensions

Strikethrough: ~~This text is deleted~~.

Autolink: https://github.com/

Email autolink: developer@example.com

Task list:

- [x] Completed
- [ ] Pending

Table:

| A | B |
|---|---|
| 1 | 2 |

## 12. Footnotes (Extension-Dependent)

Here is a sentence with a footnote.[^first]

Here is another sentence with a longer footnote.[^long]

[^first]: This is the first footnote.

[^long]: This is a longer footnote with **formatting**.

    It contains a second paragraph.

## 13. Edge Cases

An empty paragraph follows this line.


This paragraph follows a blank line.

A line containing only a backslash:
\

A line containing only an escaped asterisk:
\*

A list item containing `inline code`, **bold**, and [a link](https://example.com).

- First item
- Second item
  Continuation of the second item.

> A blockquote with a list:
>
> 1. One
> 2. Two
>
> End of blockquote.

```text
Nested-looking Markdown:
# This is code, not a heading.
- This is code, not a list.
> This is code, not a quote.
```

## 14. End of Document

The renderer should not add unexpected content after this paragraph.