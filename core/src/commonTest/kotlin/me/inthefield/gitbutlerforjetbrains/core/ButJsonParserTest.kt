package me.inthefield.gitbutlerforjetbrains.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ButJsonParserTest {

    private val fullStatusJson = """
        {
          "uncommittedChanges": [
            {"cliId": "zm", "filePath": "one.txt", "changeType": "added"},
            {"cliId": "pp", "filePath": "two.txt", "changeType": "added"}
          ],
          "stacks": [
            {"cliId": "i0", "assignedChanges": [], "branches": [
              {"cliId": "on", "name": "feature-one", "commits": [], "upstreamCommits": [], "branchStatus": "completelyUnpushed", "reviewId": null, "ci": null}
            ]},
            {"cliId": "j0", "assignedChanges": [], "branches": [
              {"cliId": "tw", "name": "feature-two", "commits": [], "upstreamCommits": [], "branchStatus": "completelyUnpushed", "reviewId": null, "ci": null}
            ]}
          ],
          "mergeBase": {"cliId": "", "commitId": "a58a", "message": "init\n"},
          "upstreamState": {"behind": 0}
        }
    """.trimIndent()

    @Test
    fun parseStatus_fullSample_yieldsBranchesAndChanges() {
        val status = ButJsonParser.parseStatus(fullStatusJson)

        assertEquals(2, status.branches.size)
        assertEquals(
            VirtualBranch("on", "feature-one", emptyList(), "completelyUnpushed"),
            status.branches[0],
        )
        assertEquals(
            VirtualBranch("tw", "feature-two", emptyList(), "completelyUnpushed"),
            status.branches[1],
        )

        assertEquals(2, status.uncommittedChanges.size)
        assertEquals(UncommittedChange("zm", "one.txt", "added"), status.uncommittedChanges[0])
        assertEquals(UncommittedChange("pp", "two.txt", "added"), status.uncommittedChanges[1])
    }

    @Test
    fun parseStatus_assignedChanges_appearInUncommittedChanges() {
        val json = """
            {
              "uncommittedChanges": [
                {"cliId": "zm", "filePath": "one.txt", "changeType": "added"}
              ],
              "stacks": [
                {"cliId": "i0", "assignedChanges": [
                  {"cliId": "aa", "filePath": "assigned.txt", "changeType": "modified"}
                ], "branches": [
                  {"cliId": "on", "name": "feature-one"}
                ]}
              ]
            }
        """.trimIndent()

        val status = ButJsonParser.parseStatus(json)

        assertEquals(2, status.uncommittedChanges.size)
        assertTrue(
            status.uncommittedChanges.any { it.cliId == "aa" && it.filePath == "assigned.txt" },
            "expected assigned.txt in uncommittedChanges",
        )

        // The stack also exposes its assignedChanges directly...
        val stack = status.stacks[0]
        assertEquals(1, stack.assignedChanges.size)
        assertEquals(UncommittedChange("aa", "assigned.txt", "modified"), stack.assignedChanges[0])

        // ...and they are the SAME instances that were merged into the flat list.
        val flat = status.uncommittedChanges.first { it.cliId == "aa" }
        assertSame(flat, stack.assignedChanges[0])
    }

    @Test
    fun parseStatus_stackWithCommits_populatesStacksBranchesAndCommits() {
        val json = """
            {
              "uncommittedChanges": [],
              "stacks": [
                {
                  "cliId": "g0",
                  "assignedChanges": [],
                  "branches": [
                    {
                      "cliId": "ch",
                      "name": "chore/branding",
                      "commits": [
                        {
                          "cliId": "8b",
                          "commitId": "8b114f9b9bf2cc122508e39e7b9c16f814419908",
                          "createdAt": "2026-07-20T18:57:28+00:00",
                          "message": "chore: rename display name to 'GitButler for JetBrains'\n\nsecond line body",
                          "authorName": "BartInTheField",
                          "authorEmail": "bart.in.t.veld@pm.me",
                          "conflicted": false,
                          "reviewId": null,
                          "changes": null
                        }
                      ],
                      "upstreamCommits": [],
                      "branchStatus": "nothingToPush",
                      "reviewId": null,
                      "ci": null
                    }
                  ]
                }
              ],
              "mergeBase": {"cliId": "", "commitId": "a58a"},
              "upstreamState": {"behind": 0}
            }
        """.trimIndent()

        val status = ButJsonParser.parseStatus(json)

        assertEquals(1, status.stacks.size)
        val stack = status.stacks[0]
        assertEquals("g0", stack.cliId)
        assertEquals(1, stack.branches.size)

        val branch = stack.branches[0]
        assertEquals("ch", branch.cliId)
        assertEquals("chore/branding", branch.name)
        assertEquals("nothingToPush", branch.branchStatus)

        // flat branches list holds the same branch instance as the stack
        assertEquals(1, status.branches.size)
        assertSame(stack.branches[0], status.branches[0])

        assertEquals(1, branch.commits.size)
        val commit = branch.commits[0]
        assertEquals("8b", commit.cliId)
        assertEquals("8b114f9b9bf2cc122508e39e7b9c16f814419908", commit.commitId)
        assertEquals("BartInTheField", commit.authorName)
        assertEquals("2026-07-20T18:57:28+00:00", commit.createdAt)
        assertFalse(commit.conflicted)
        // multi-line message kept verbatim
        assertEquals(
            "chore: rename display name to 'GitButler for JetBrains'\n\nsecond line body",
            commit.message,
        )
    }

    @Test
    fun parseStatus_commitWithChanges_populatesCommitChanges() {
        val json = """
            {
              "uncommittedChanges": [],
              "stacks": [
                {"cliId": "g0", "assignedChanges": [], "branches": [
                  {"cliId": "ch", "name": "chore/branding", "branchStatus": "nothingToPush", "commits": [
                    {
                      "cliId": "8b",
                      "commitId": "8b114f9",
                      "message": "msg",
                      "authorName": "Bart",
                      "createdAt": "2026-07-20T18:57:28+00:00",
                      "conflicted": false,
                      "changes": [
                        {"cliId": "tp:x", "filePath": "one.txt", "changeType": "modified"},
                        {"cliId": "tp:y", "filePath": "two.txt", "changeType": "added"}
                      ]
                    },
                    {
                      "cliId": "9c",
                      "commitId": "9c225f0",
                      "message": "msg2",
                      "authorName": "Bart",
                      "createdAt": "2026-07-20T18:58:00+00:00",
                      "conflicted": false
                    }
                  ]}
                ]}
              ]
            }
        """.trimIndent()

        val status = ButJsonParser.parseStatus(json)

        val commits = status.branches[0].commits
        val commitWithChanges = commits[0]
        assertEquals(2, commitWithChanges.changes.size)
        assertEquals(
            UncommittedChange(cliId = "tp:x", filePath = "one.txt", changeType = "modified"),
            commitWithChanges.changes[0],
        )
        assertEquals(
            UncommittedChange(cliId = "tp:y", filePath = "two.txt", changeType = "added"),
            commitWithChanges.changes[1],
        )

        // no "changes" field -> emptyList()
        assertTrue(commits[1].changes.isEmpty())
    }

    @Test
    fun parseStatus_missingCommitsAndBranchStatus_defaultToEmpty() {
        val json = """
            {
              "uncommittedChanges": [],
              "stacks": [
                {"cliId": "g0", "assignedChanges": [], "branches": [
                  {"cliId": "ch", "name": "chore/branding"}
                ]}
              ]
            }
        """.trimIndent()

        val status = ButJsonParser.parseStatus(json)

        val branch = status.branches[0]
        assertTrue(branch.commits.isEmpty())
        assertEquals("", branch.branchStatus)
    }

    @Test
    fun parseStatus_conflictedNull_treatedAsFalse() {
        val json = """
            {
              "uncommittedChanges": [],
              "stacks": [
                {"cliId": "g0", "assignedChanges": [], "branches": [
                  {"cliId": "ch", "name": "chore/branding", "branchStatus": "nothingToPush", "commits": [
                    {"cliId": "8b", "commitId": "8b114f9", "message": "msg", "authorName": "Bart", "createdAt": "2026-07-20T18:57:28+00:00", "conflicted": null}
                  ]}
                ]}
              ]
            }
        """.trimIndent()

        val status = ButJsonParser.parseStatus(json)

        val commit = status.branches[0].commits[0]
        assertFalse(commit.conflicted)
    }

    @Test
    fun parseStatus_emptyCollections_yieldEmptyLists() {
        val json = """
            {"uncommittedChanges": [], "stacks": []}
        """.trimIndent()

        val status = ButJsonParser.parseStatus(json)

        assertTrue(status.branches.isEmpty())
        assertTrue(status.uncommittedChanges.isEmpty())
    }

    @Test
    fun parseCommitResult_success_returnsOkWithCommitId() {
        // but 0.22 emits a flat {commitId, changeId, branch} object on success.
        val json = """
            {"commitId": "35902587083f73a3", "changeId": "tslomtqmxlxosvkr", "branch": "feat"}
        """.trimIndent()

        val result = ButJsonParser.parseCommitResult(json)

        assertTrue(result is ButResult.Ok)
        assertEquals("35902587083f73a3", (result as ButResult.Ok).value)
    }

    @Test
    fun parseCommitResult_missingCommitId_isSuccessWithUnknownId() {
        // Exit code 0 is the success signal; an output shape without "commitId"
        // must not be reported as a failed commit.
        val json = """{"status": {"uncommittedChanges": [], "stacks": []}}"""

        val result = ButJsonParser.parseCommitResult(json)

        assertTrue(result is ButResult.Ok)
        assertEquals("", (result as ButResult.Ok).value)
    }

    @Test
    fun parseCommitResult_errorJson_returnsErrWithMessage() {
        val json = """
            {"error": "setup_required", "message": "No GitButler project found at .", "hint": "run `but setup` to configure the project"}
        """.trimIndent()

        val result = ButJsonParser.parseCommitResult(json)

        assertTrue(result is ButResult.Err)
        assertEquals("No GitButler project found at .", (result as ButResult.Err).message)
    }

    @Test
    fun parseErrorMessage_setupRequiredSample_returnsMessage() {
        val json = """
            {"error": "setup_required", "message": "No GitButler project found at .", "hint": "run `but setup` to configure the project"}
        """.trimIndent()

        assertEquals("No GitButler project found at .", ButJsonParser.parseErrorMessage(json))
    }

    @Test
    fun parseErrorMessage_onlyErrorField_fallsBackToError() {
        val json = """{"error": "boom"}"""
        assertEquals("boom", ButJsonParser.parseErrorMessage(json))
    }

    @Test
    fun parseErrorMessage_rawOnUnparseable_returnsInput() {
        val raw = "not json at all"
        assertEquals(raw, ButJsonParser.parseErrorMessage(raw))
    }

    @Test
    fun parseStatus_malformedJson_throws() {
        // parseStatus propagates the underlying kotlinx.serialization exception; the client wraps it in Err.
        assertFailsWith<Exception> {
            ButJsonParser.parseStatus("{ this is : not valid")
        }
    }
}
