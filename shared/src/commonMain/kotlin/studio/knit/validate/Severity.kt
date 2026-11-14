package studio.knit.validate

/**
 * - [ERROR]: the pattern is mathematically inconsistent and cannot be knitted as written.
 * - [WARNING]: unusual and possibly a mistake, but may be intentional.
 * - [INFO]: a suggestion; nothing is wrong.
 */
enum class Severity { ERROR, WARNING, INFO }
