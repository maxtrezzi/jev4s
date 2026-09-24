# Jev concepts

This guide explains how Jev works, for both Scala versions. It starts with one small request and
adds one idea at a time. The tutorials show the same ideas in code:
[Scala 3](scala3.md) and [Scala 2.13](scala213.md).

The facts about Jev come from TypeSafe AI's documentation at
[docs.typesafe.ai](https://docs.typesafe.ai/introduction.md). jev4s is not part of TypeSafe AI:
when this guide and that documentation disagree, the documentation is right, and this guide
has a bug.

## Contents

1. [A first request](#1-a-first-request)
2. [The state](#2-the-state)
3. [Three kinds of question](#3-three-kinds-of-question)
4. [Probabilities and confidence](#4-probabilities-and-confidence)
5. [How to write good questions](#5-how-to-write-good-questions)
6. [Many questions in one call](#6-many-questions-in-one-call)
7. [Questions with structure](#7-questions-with-structure)
8. [Models, cost and limits](#8-models-cost-and-limits)
9. [How jev4s talks to Jev](#9-how-jev4s-talks-to-jev)

## 1. A first request

A large language model writes text for people. When your code needs a decision, such as "which
team should handle this ticket?", it has to find the decision inside that text. Jev works in a
different way. It does not write text. You send it:

- a **state**: the content to judge, such as a support ticket;
- one or more **questions** about the state, each with the possible answers.

Jev returns one **typed answer** for each question, with **probabilities**. Your code can use
the answers directly: in an `if`, in a `match`, to sort, or to choose a route.

This is a real request, sent to the API as JSON. It asks one yes/no question about a message:

```json
{
  "state": "Help! My payouts have been failing for 3 days and I have a launch tomorrow.",
  "model": "jev-1.13.0",
  "questions": {
    "is_urgent": { "type": "noul", "instructions": "Does this convey urgency?" }
  }
}
```

And this is the reply:

```json
{
  "model": "jev-1.13.0",
  "answers": { "is_urgent": { "type": "noul", "noul": 0.98 } },
  "usage": { "input_tokens": 289, "output_tokens": 23 }
}
```

`0.98` is the probability that the answer is "yes". With jev4s you never write this JSON: you
write the question in Scala, and you read `0.98` as a `Probability`. The name `is_urgent` is
yours: the answer comes back under the same name.

TypeSafe calls this kind of model **System One**, after the fast, intuitive thinking that Daniel
Kahneman calls "System 1". Jev makes quick, focused judgments. It does not reason for a long
time, and it does not explain its answers.

## 2. The state

The state is the content that Jev reads and judges. It is the most important input of a request:
a question can only be answered from what the state contains. TypeSafe describes it as the
material you would give to a panel of experts before you ask them for a judgment.

A state can be:

| Form | Good for | Example |
|---|---|---|
| Text | One message or one passage | `"My card was charged twice."` |
| A JSON object | Several named parts that belong together | `{"message": "...", "order_id": "A-104"}` |
| A JSON array | A list of messages or records | `["Hi", "My card was charged twice."]` |

Text is enough when there is one piece of content, as in the first request. For most real
decisions, use a **JSON object**: the message, who wrote it, the records it is about, and the
rules that apply, each under its own name. This is one state for a shop's support desk:

```json
{
  "message": "You charged me twice for my boots! I want the second payment back before Friday.",
  "customer": { "name": "Ana", "plan": "pro" },
  "order": { "id": "A-104", "item": "Hiking boots", "charges_usd": [89.0, 89.0] },
  "refund_policy": "A duplicate charge is refunded at once. Any other refund needs the item back, unused, within 30 days."
}
```

**A question points at a part of the state by its name, between backticks.** For a part inside
another, write the path with dots, and with an index for an element of a list:
`order.charges_usd`, or `messages[0].text`. Jev then knows which part to judge:

```json
{
  "asked":     { "type": "noul", "instructions": "Does `message` ask for money back?" },
  "duplicate": { "type": "noul", "instructions": "Does `order.charges_usd` contain the same amount twice?" },
  "allowed":   { "type": "noul", "instructions": "Does `refund_policy` allow a refund at once for this ticket?" }
}
```

Each question checks one fact, in the part of the state where it is. Your code then joins the
answers: refund at once when all three are "yes". The state is sent once, and every question in
the request reads the same state.

A few rules for a good state:

- **Put together what a question must compare.** The policy is not part of the ticket, but the
  question `allowed` compares them, so both go in the same state.
- **Give each part a clear name.** `refund_policy` tells Jev more than `text2`.
- **Leave out what no question needs.** Every part costs input tokens, and a state has a size
  limit (see [Models, cost and limits](#8-models-cost-and-limits)).
- **Keep facts in the state and judgments in the questions.** "The customer wants a refund" is a
  judgment: ask it as a question, do not write it in the state.

When the data for one question is not part of the content, such as a record to compare the
message with, it can travel with that question instead: see
[Questions with structure](#7-questions-with-structure).

Jev reads text only: no images, no audio, no video. English gives the best results. Other
languages work, but less well, so test them with your own data.

In jev4s, the state is a `String`, a `ujson.Value`, or a value of your own type, such as a
`Ticket`, with a `ToState` that turns it into JSON. The tutorials use exactly this example.

## 3. Three kinds of question

Every question has three parts:

- a **name**, chosen by you, such as `is_urgent`. It identifies the answer. **Jev never sees
  the name**, so the instructions must contain the whole question.
- the **instructions**: the question, or a statement to judge.
- the **criteria**: the possible answers. Their form depends on the kind of question.

There are three kinds:

| Kind | It answers | You give | You get back |
|---|---|---|---|
| **Noul** | Is this true? | Optionally, what "yes" and "no" mean | The probability of "yes" |
| **Score** | Where is it on a scale? | 2 to 10 ordered levels | A position on the scale, a probability for each level, a confidence |
| **Choice** | Which one of these? | 1 to 255 options | The most likely option, a probability for each option, a confidence |

Choose the kind from the shape of the answer your code needs. A Noul maps to an `if`. A Score
maps to a threshold or a ranking. A Choice maps to a `match` with one branch for each option.

### Noul: yes or no

A Noul answer is one number from 0 to 1: the probability that the answer is "yes". A value near
1 is a clear yes, a value near 0 is a clear no, and a value near 0.5 means that Jev gives yes and
no about the same probability.

You can add criteria that say what counts as "yes" and what counts as "no". The API calls them
`true` and `false`; jev4s calls them `whenTrue` and `whenFalse`:

```json
{
  "type": "noul",
  "instructions": "Has the customer contacted support about this before?",
  "criteria": {
    "true": "Mentions a prior attempt, ticket, or that they have asked before",
    "false": "No sign of any previous contact"
  }
}
```

Most Nouls need only the instructions. Try your questions with and without criteria, and keep
the version that gives better answers on your own data.

A Noul measures how probable "yes" is. It does **not** measure how much of something there is.
If you ask "Is the candidate strong in Python?", 0.5 does not mean "medium skills": it means
that Jev is not sure. To measure an amount, use a Score.

### Score: a position on a scale

A Score has 2 to 10 **levels**, in order from low to high. Level numbers start at 0:

```json
{
  "type": "score",
  "instructions": "How frustrated is the customer?",
  "criteria": ["Calm", "Frustrated", "Very angry"]
}
```

Jev gives each level a probability, and the probabilities add up to 1. The **score** is each
level number multiplied by its probability, added together. This is a real reply to the question
above:

```json
{ "type": "score", "score": 1.19, "confidence": 0.71,
  "probabilities": { "0": 0.0, "1": 0.81, "2": 0.19 } }
```

`0 × 0.0 + 1 × 0.81 + 2 × 0.19 = 1.19`: the customer is "Frustrated", and a little towards
"Very angry". Three things follow from this:

- The score can fall **between two levels**. You can use it to sort. When your code needs one
  level, take the level with the highest probability, which jev4s gives as `mostLikely`, and
  not the level nearest to the score: with probabilities 0.5, 0.0 and 0.5, the score is 1.0,
  and level 1 has no chance at all.
- **Different probabilities can give the same score.** A score of 1.0 can mean "all on level 1",
  or "half on level 0 and half on level 2". Read the probabilities and the confidence too.
- A scale with 3 levels goes from 0 to 2, and a scale with 5 levels from 0 to 4. To compare or
  combine two Scores, first divide each score by its highest level number: then both go from 0
  to 1.

Jev judges **each level on its own**: it does not see the level numbers or the levels next to
it. So a level must describe a situation, such as "Broken, but a workaround exists", and not a
degree, such as "Quite severe" or "Worse than level 1".

### Choice: one option out of many

A Choice has 1 to 255 options. Each option has a **key**, such as `billing`, and an optional
**description**. Jev reads both, so a good description separates an option from the others. Use
`null` when the key says enough:

```json
{
  "type": "choice",
  "instructions": "Which team should handle this?",
  "criteria": {
    "billing": "Payments, invoicing, refunds",
    "technical": "Bugs, outages, integrations",
    "sales": null
  }
}
```

The answer is the option with the highest probability, the probability of every option, and a
confidence. This is a real reply to the question above, for the payouts message of the first
request:

```json
{ "type": "choice", "choice": "billing", "confidence": 0.86,
  "probabilities": { "billing": 0.91, "technical": 0.09, "sales": 0.0 } }
```

Jev always chooses one of your options. If an input may fit none of them, add an option such as
`other`, so that Jev has a correct answer to give.

## 4. Probabilities and confidence

Jev is trained so that its probabilities are **calibrated**: over many answers given with a
probability of 0.8, about 80% are right. This holds for groups of answers. It does not promise
that one single answer is right.

A Choice or a Score also has a **confidence**, from 0 to 1. It is computed from the
probabilities: when they are all on one option or level, the confidence is high; when they are
spread over several, it is low. A Noul has no confidence, because its one number already says
everything.

Low confidence is useful information. On a Choice, it often means that no option is clearly
better than the others. On a Score, it often means that the levels overlap, that the question
measures more than one thing, or that the state does not say enough.

A good way to start is to split the answers into three ranges:

| Confidence | What your code does |
|---|---|
| High | Acts on the answer automatically. |
| Medium | Acts with care: asks the user to confirm, or marks the case for review. |
| Low | Does not act: sends the case to a person, or to a slower system. |

The limits between the ranges depend on the cost of a mistake. An action that deletes or pays
needs a higher limit than an action that only reads. The same goes for a Noul: use 0.5 when a
wrong "yes" and a wrong "no" cost the same, a higher limit when a wrong "yes" is expensive
(such as a refund), and a lower one when a missed "yes" is expensive (such as a safety problem).
Start with careful limits, test them with your own data, and change them in your code.

## 5. How to write good questions

**Ask for one quick judgment.** A good question is one that an expert answers in a second, given
the right content. "Does this message convey urgency?" is good. "Analyse this message and
decide what to do" is not: it needs slow reasoning.

**Split a judgment that depends on several things.** Instead of "How good is this startup
pitch?", ask three Scores: the size of the market, how feasible the technology is, and how
different the product is. Then combine them in your code with weights of your own. When your
priorities change, you change a number in the code, not a question.

**One condition for each Noul.** "Is the customer angry and asking for a refund?" asks two
things at once. Ask two Nouls, and combine them with `&&`.

**Make "yes" the answer your code looks for.** "Does the message contain personal data?" is
clear. "Is the message free of personal data?" makes a high value mean "no", and code that reads
it later will get it wrong.

**One dimension for each Score.** A level such as "punctual, smart and experienced" measures
three things. A person who is punctual but not experienced fits no level, the confidence drops,
and the score means less.

**Describe situations, not degrees.** "Broken, but a workaround exists" gives Jev something to
compare with the state. "Moderately severe" does not.

**Test with your own data.** Two versions of the same question can give different results. Keep
a few examples for which you know the right answer, and check each change against them. A
higher confidence alone does not prove that a question is better.

## 6. Many questions in one call

Send every question about the same state in **one request**. You can mix the three kinds. Jev
answers all the questions in parallel, so more questions add almost no time, and each question
costs only its own few tokens. TypeSafe measured 13 questions in one call as 11.5 times cheaper
and 9.6 times faster than 13 calls.

Every question is **independent**: Jev answers each one as if it were alone. One answer is never
context for another question, so you can add or remove a question without changing the others.

This makes it cheap to ask **speculative** questions: questions whose answer matters only for
some inputs. Ask for the severity of a bug on every ticket, and ignore it in your code when the
ticket is not a bug report.

Make a **second call** only when the second request cannot exist without the first answer: when
the answer decides which data to put in the next state, or which options to offer next. If the
second questions could be asked about the first state, ask them in the first call.

## 7. Questions with structure

Instructions, the criteria of a Noul, the levels of a Score and the descriptions of a Choice's
options can be **text or JSON**. Start with text. Use JSON when a question needs data next to
it, or when a level needs examples:

```json
{
  "type": "noul",
  "instructions": {
    "potential_duplicate": { "name": "Jon Smith", "location": "Oakland, CA", "last_employer": "Google" },
    "question": "Is the resume for the same person as `potential_duplicate`?"
  }
}
```

Put the question in one field and the data in the others, and name the data with backticks, as
you do for the state. This is useful when your code builds many questions with the same text and
different data, such as one question for each record in a database.

For the levels of a Score, JSON lets you add examples to each level. Use the same field names on
every level, so that Jev compares like with like:

```json
"criteria": [
  "Harmless",
  { "summary": "Suspicious", "signals": ["Unusual sender", "Urgent tone"] },
  { "summary": "Dangerous", "signals": ["Asks for credentials or money"] }
]
```

Examples help only when they look like your real inputs. Choose examples for which you know the
right level, and test the new version on other inputs before you keep it. TypeSafe's page
[Advanced: structure](https://docs.typesafe.ai/primitives/advanced.md) has more examples.

## 8. Models, cost and limits

**The model.** Every request names a model, such as `jev-1.13.0`. TypeSafe also has aliases,
such as `jev-latest`, which move to each new version. A new version can give different
probabilities, so a limit that you tested on one version may not fit the next. For this reason
jev4s has no default model: you always name one. The reply says which model answered.

**The cost.** TypeSafe charges for **input tokens**: the state and the questions. Output tokens
are free. A request with one short question costs a few hundred input tokens.

**The limits**, for `jev-1.13.0`, from TypeSafe's page
[Models](https://docs.typesafe.ai/models.md):

- 64,000 tokens for one request: the state and all the questions together;
- 32,000 tokens for the state and the longest question;
- a limit on requests per minute and on tokens per second, which TypeSafe changes often. Above
  it, the API answers "429 Too Many Requests".

## 9. How jev4s talks to Jev

This part is about jev4s, not about Jev. It is the same in both Scala versions.

### The concepts in Scala

| Concept | Scala 3 | Scala 2.13 |
|---|---|---|
| A client | `JevClient(config)` | `JevClient.create(config)` |
| The state | A `String`, a `ujson.Value`, or your type with a `given ToState` | The same, with an `implicit ToState` |
| A question | `Noul`, `Score[L]`, `Choice[C]` | `Noul`, `Score[L]`, `Choice[C]` |
| Its name | A name in a named tuple: `(urgent = Noul(...))` | A key: `Noul(...).as("urgent")` |
| The options of a Choice | `enum Team derives JevChoice` | An `implicit JevChoice[Team]` |
| The levels of a Score | Text, or `enum Mood derives JevScale` | Text, or an `implicit JevScale[Mood]` |
| The answers | A named tuple: `r.urgent` | `Answers`: `answers.get(urgent)` |
| A probability | `Probability`, from 0 to 1 | `Probability`, from 0 to 1 |
| Questions built at runtime | `client.askMap(state, Map(...))` | Keys built at runtime |

The answer types are the same in both versions:

| Question | Answer | Fields |
|---|---|---|
| `Noul` | `NoulAnswer` | `probability`, and `isYes` for a probability of 0.5 or more |
| `Score[L]` | `ScoreAnswer[L]` | `score`, `mostLikely` (an `L`), `confidence`, `probabilities` keyed by `L`: a value of your type, or each level as you wrote it |
| `Choice[C]` | `ChoiceAnswer[C]` | `choice` (a `C`), `confidence`, `probabilities` keyed by `C`, and `ifConfident` |

### Checks before sending

jev4s checks a request before it sends it, and returns **every** problem at once, as
`JevError.InvalidRequest(problems)`. It finds a request with no questions, an empty or repeated
name, a Score with fewer than 2 or more than 10 levels or with a repeated level, and a Choice with
no options, more than 255 options, or a repeated key. A request with problems costs nothing,
because it is not sent. In Scala 3, a Score over an enum with fewer than 2 or more than 10 cases
does not even compile.

### Errors

jev4s does not throw. Every call returns `Either[JevError, A]`, and `error.isRetryable` tells you
if sending the same request again can help.

| Error | What it means | Retried |
|---|---|---|
| `InvalidRequest(problems)` | jev4s found problems before sending, for example 11 levels in a Score | no |
| `Unauthorized` | HTTP 401: the API key is missing or wrong | no |
| `Rejected(message)` | HTTP 400 or 422: Jev refused the request, for example for an unknown model | no |
| `RateLimited(retryAfter)` | HTTP 429: too many requests | yes |
| `Overloaded` | HTTP 529: Jev is busy | yes |
| `ServerError(status, message)` | HTTP 408 or another 5xx | yes |
| `Network(message)` | no response: a timeout, or no connection | yes |
| `Unexpected(status, message)` | any other status, for example 404 for a wrong base URL | no |
| `Decoding(message)` | the response could not be read | no |

### Retries

The client retries a retryable error 2 times. It waits 0.5 s before the first retry, and doubles
the wait each time, up to 5 s. When the server sends a `Retry-After` header of 60 s or less,
the client waits that long instead. These are the defaults of the official SDKs.

A call does not retry for more than 30 s in total. When the next wait would end later, the client
stops and returns the last error. If the server asks for a longer wait, you get
`RateLimited(Some(delay))`, and your code decides what to do. You can change every one of these
numbers in `RetryPolicy`, or turn retries off with `RetryPolicy.none`.

### Events, not logs

jev4s never writes logs. It gives each event to a function of yours, `onEvent`:

- `JevEvent.Replied(reply)` after each successful call, with the model that answered and the
  input tokens it cost, when the reply reports them;
- `JevEvent.Retrying(error, retry, delay)` before each retry.

Send them to your logger or your metrics.

### The API key

`JevConfig.fromEnv(model)` reads the key from `TYPESAFE_API_KEY`, and the address of the API from
`TYPESAFE_BASE_URL` when it is set: the same variables as the official SDKs. The key is kept in
an `ApiKey`, which always prints as `<hidden>`, so it does not appear in a log by mistake. The
base URL must use `https`. Plain `http` works only for `localhost`, because the key would
travel unencrypted. `fromEnv` checks this; a `JevConfig` that you build yourself is not checked,
so give it an `https` base URL.
