rank-profile ranking_b {
    function constant_value_b() {
        expression: 1
    }
    first-phase {
        expression: constant_value_b
    }

    rank-profile debug inherits ranking_b {
        match-features {
            constant_value_b
        }
    }
}
