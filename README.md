---
kernelspec:
  name: python3
  display_name: 'Python 3'
---

How to get going?
```shell


brew install imagemagick
brew install ghostscript
brew install mactex
brew install typst 
```


aaaa
% Embed both the input and output

:::{card} With code

```{embed} ./notebooks/first.ipynb#mylabel
:remove-output: false
:remove-input: false
```

:::

aaa

![](#embed#./notebooks/first.ipynb#mylabel)

aaaa

```{code-cell} python
:label: markdown-myst-2
:tags: [remove-cell]

import qq
```

```{code-cell} python
:label: python-import-from-directory
:caption: My Notebook Cell Caption

import mycode.test as utils

print(utils.foo())
```

This is *emphasized*, **bold**, `inline code`, and [a link](https://Wikipedia.org).

```python
print("""
This is a code block
""")
```
> And this is a quote!


```{code-cell} python
:label: markdown-myst
print("Here's some python!")
```


And here I reference [](#markdown-myst).
