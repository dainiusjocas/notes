---
thumbnail: _static/filter-subspaces-threshold.png
title: Tensor Filtering with Dynamic Thresholds
date: 2026-08-01
---

TL;DR:
```text
function filter_subspaces_above_threshold(t, threshold) {
    expression: filter_subspaces(t - threshold, f(x)(x > 0)) + threshold
}

function filter_subspaces_below_threshold(t, threshold) {
    expression: filter_subspaces(t - threshold, f(x)(x < 0)) + threshold
}
```

## The Issue

The somewhat new builtin `filter_subspaces` [function](https://docs.vespa.ai/en/reference/ranking/ranking-expressions.html#filter-subspaces) 
promises to produce a new tensor containing only the subspaces that match the filter.
The main limitation is that filtering is done with a [lambda](https://docs.vespa.ai/en/reference/ranking/ranking-expressions.html#lambda)
and in Vespa lambda expressions 

> cannot access variables or data structures outside the lambda, i.e., they are not closures. 

i.e., the filter cannot access neither document attributes, nor query inputs, nor constants, and not even other functions, 
i.e., you need to know the filter value at application build time, 
i.e., the threshold can't be dynamic[^example].

## The Workaround

There is a neat trick to filter on a dynamic scalar value:
1. **subtract** the threshold value from the tensor
2. `filter_subspaces` on (1) for being larger or smaller than 0
3. **add** the threshold value back to (2)[^calculus].

Or one-liner:
```text
filter_subspaces(t - threshold, f(x)(x > 0)) + threshold
```

Example:
```text
filter_subspaces(
    tensor<float>(chunk{}):{0:13,1:7,2:5,3:15,4:30,5:2} - 10, 
    f(x)(x > 0)
) + 10
=> tensor<float>(chunk{}):{0:13.0, 3:15.0, 4:30.0}
```

Check the [Tensor Playground runnable example](https://docs.vespa.ai/playground/#N4KABGBEBmkFxgNrgmUrWQPYAd5QGNIAaFDSPBdDTAF30jFoAsBTMARwFcBDAGwCWtAJ5gs0MPz5gCzLgDsA1gGdIZVAF91G0hmrlcDViXUQK+fTUjyGtAIwmamYwki1W85VgBOAHmh8WDy0AHwAFLIKisAaAJRwwAAMcHYAzMR2cADsxABMcACsxKkpRQAscKmJxAVwuRpqTloYOuqWZoauRLpWlGimdAxgAHJgAspgACbC8jwAtgJEA82aPRDtUJ1QxmuYfRtmNq7Djk7bDKmNNCsQrXoD2H2Q3Q-7A2b0rmDQAvKTYt5JqxvL8AOZiCQEVh8PiqZbaXYbR5GU7kN5nKBHKC0XKoqwuQjQvgAfR8QO8YXsxDAcx4AA9YlcWgi2g8ts88WZ0Wc3ENaYp2DwaTxlIpvj4mGwwAAjVjKWgjJmaFn3JzI1w7V4Wd6Y2yXXbkAluXK+E7wlqItlPF5q7lqz5QKbeXCSGFgZRcaXKHA8KETaDOuaS9i00WS4KSbzsaD8ZTGc2rVlq9ma23ajHWWxlTnOBg-PjubzEj1en1+ynpb5hZSxauxRkJ26W5PWnPmKg63lfAhYObS37RgQF4Gsf6hsUAdyEzABAlBv34TA8Xm84tXUY9BaVtxVqCRKbbdqsWLcBRzZiNtDKACp7NuwDcH82rAeDVAj+QTzxz+dXPYwAAtEung+P4gTBOEkRKDE8RJCkqQAHTVGAJR2AUSHUhUVRIQ0jZPkmL5PKmvTpjyX48N+b4XrYy4+GEPAxMQdKILkAC6sEADqQBwXEIIgdg1Kx1JcROvFIHY1S5IkrG4U0u7rFaKJvu2-QZieP6QEa+aFsWnrer6cr0RR1LQGEDJmWAIRgAU9b3o+dx7opGqHqRarqVRv5QNpwK6aWBnKJSdiAWAEkmWZtZ0pZYCJPWYAANQhYkdnaCgrEgBoQA).

## The Discussion

The inconvenient part is that we need two functions for filtering above or below the threshold.

If your threshold is another tensor[^thresholds], 
make sure it has all the same dimensions as the tensor you want to filter
because the `-` is `join`'ed on the target tensor, i.e., non-common dimensions are dropped.

What if the tensor has an indexed dimension?
```text
filter_subspaces(tensor(a{},x[2]):{"q": [1,5], "w": [10,20]}, f(x)(x > 5))
=> tensor(a{},x[2]):{w:[10.0, 20.0]}
```
i.e., at least one value within the indexed dimension should pass the threshold.

## Final Thoughts

I believe filtering on some threshold is a pretty common use case of `filter_subspaces`, 
and this trick should be mentioned somewhere.
Or maybe even become a built-in function.

### P.S.

For more tensor fun, check [The Advent fo Tensors](https://blog.vespa.ai/advent-of-tensors-2025/) 

[^calculus]: Yeah, like back in the calculus class, we've been adding and then subtracting 1 to rewrite the expression.
[^example]: Also, there is another approach of filtering by a dynamic value in the [Tensor Playground with masking](https://docs.vespa.ai/playground/#N4IgZiBcDaoPYAcogMYgDQiZUAXZABLgBYCmBAjgK4CGANgJa4CeBcYB9dBKxVAdgGsAziAC+Y9PGwhSGLFFD9kuAIzy5kELlL9hcAE4AeMHTg1cAPgAUvAYOBiAlDgAMkVQGZ0qyAHZ0ACZIAFZ0Tw8wgBZIT1d0EMhAsXFJaWQ0TGw8QgA5AgZhAgATZn4aAFsGNAkpEERkOSzFEGUtXI1kT1S6hq1MhRxtQjAGfmK2A2LSAzGAczYOFFI6OlFa9K0mwaUVQM7+lboAfUNpg2s1dAIKmgAPJx7N1Hls4a0bmkFyGk-hQQIYEMRDIBAARqRhLgCB0NvUZNs3m1tN1MJptIEjLC0vCMq8WvgPsUDIhOKsCMIqGDhAgaMsimASRUQeRbv8QRZOAZyGB6MI5HC+rJ8UNkbgogdwAw6DoDMdKdTafTLt5AdZhE51U5HoKZAM3oSQDw4BUwWMedLZaQJmyAQB3JjESYMOZjehEXT6AyA4Hcykyp64rYi3ZaXAhSXigBUalSAF0xEA).
[^thresholds]: i.e., a collection of thresholds.
