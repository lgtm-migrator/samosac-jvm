<SLANG>

needs {
    java::lang::System
}

let khana(stringArg: string) {
    (stringArg) -> putout.
}

bro, wasCalled = 0.

let mana(var1: int, var2: string): int {
    wasCalled = wasCalled + 1.
    if (var1 == 0) {
        ("base case") -> putout.
        return 4.
    }

    (var2) -> putout.
    return (var1 - 1, var2) -> mana.
}

bro, num: int = (() -> putinString) -> stoi.
bro, result: int = 50 + (num, "gogo") -> mana.
("Result: " + (result) -> itos) -> putout.
("wasCalled: " + (wasCalled) -> itos + " times") -> putout.
("Hello world!") -> putout.
</SLANG>