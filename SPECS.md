# kt2ts — specs for a future rewrite

Captured while patching 0.0.14 → 0.0.15. To preserve when rewriting with a
proper Kotlin parser (rather than KSP).

## Inputs

- A set of root classes (currently picked via `@GenerateTypescript`).
- A `kt-to-ts-mappings.json`: qualified class name → relative TS file path.
- A `modifiedFiles` set: the files belonging to the module being compiled.

## Outputs

For each root class, generate the transitive closure of referenced classes as
TypeScript. Filtering and dedup must operate on stable identity
(`qualifiedName`), not on parser instance equality.

## Required behavior

### Type parameters

- A type parameter (`T`, `K`, …) is **not** a dependency. The parser must not
  attempt to descend into it. In KSP-land, this manifests as
  `KSTypeParameter`; the code was casting to `KSClassDeclaration` and
  crashing with `IllegalArgumentException("T")`.
- A type parameter has a parent declaration (the class that declares it).
  When rendering its name, do **not** prefix it with the parent. The naive
  `parent$ + simpleName` rule produced invalid TS identifiers like
  `InsertsDto$T`.

### Generic declarations

- A class with type parameters must be declared as `interface Foo<T1, T2…>`
  using the simple names of its parameters.

### Generic usages (call sites)

- At a property type site, serialize the **effective** type arguments from
  the type reference (`KSTypeReference.element.typeArguments` in KSP),
  recursively. Example: `InsertsDto<ProviderInformations>` not `InsertsDto`,
  `List<InsertsDto<X>>` not `List<InsertsDto>`.

### Dedup / recursion termination

- The parser's dedup set must key on `qualifiedName.asString()` (or
  equivalent canonical form), because parser-level type equality is not
  stable across resolution calls. Comparing raw `KSType` instances caused
  `StackOverflowError` once the type-parameter crash was bypassed.
- Apply the same canonical-name dedup to sealed subclasses recursion.

### Cross-module references

- Any referenced type whose declaring file is outside `modifiedFiles` is
  dropped from the result. Such types must therefore be in the mappings
  file, or they'll be emitted in generated TS but never declared/imported.
- Recommendation for the rewrite: log a warning (or fail) on every
  referenced-but-not-emitted-and-not-mapped type, so silent gaps don't
  reach the frontend build.

### Defensive parsing

- If a declaration is neither a class nor a type parameter (type alias,
  unknown kind…), the parser should skip it gracefully rather than throw.

## Concrete failure modes seen (0.0.14)

1. `IllegalArgumentException("T")` — generic property like
   `InsertsDto<T>(insertDtos: List<T>)` traversed.
2. `StackOverflowError` — dedup using raw type equality, infinite revisit.
3. Generated TS with `InsertsDto$T[]` — type parameter name prefixed by
   its declaring class.
4. Generated TS using `InsertsDto` without type arguments at the call
   site — type arguments not serialized.

## Fixes applied in 0.0.15

- `ClassParser.parse`: return `data` instead of throwing when declaration is
  not a `KSClassDeclaration`; dedup by qualified name; same for sealed
  subclasses.
- `ClassParser.mapDependencies`: skip references whose resolution is a
  `KSTypeParameter`.
- `ClassWriter.className`: special-case `KSTypeParameter` (no parent prefix);
  append `<T, K…>` for declarations of generic classes.
- `ClassWriter.propertyTypeName` (new): serialize actual type arguments at
  property usage sites, recursively.
