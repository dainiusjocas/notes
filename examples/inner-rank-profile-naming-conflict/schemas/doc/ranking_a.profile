rank-profile ranking_a {
    function constant_value_a() {
        expression: 0
    }
    first-phase {
        expression: constant_value_a
    }

    rank-profile debug inherits ranking_a {
        match-features {
            constant_value_a
        }
    }
}
