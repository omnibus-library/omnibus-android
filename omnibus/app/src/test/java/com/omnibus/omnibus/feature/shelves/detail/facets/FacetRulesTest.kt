package com.omnibus.omnibus.feature.shelves.detail.facets

import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.data.shelves.FilterMode
import com.omnibus.omnibus.data.shelves.MAX_FILTER_VALUES
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

class FacetRulesTest : DescribeSpec({

    describe("ordering the chips") {

        it("#232 TC-23: lists every field in field order when no facet is set") {
            facetChips(emptyList()).map { it.field } shouldBe FilterField.entries
        }

        it("#232 TC-23: lists the set facets first, in field order, then the unset ones") {
            val author = FilterClause(FilterField.Author, FilterMode.Include, listOf("Le Guin"))
            val genre = FilterClause(FilterField.Genre, FilterMode.Include, listOf("Fantasy"))

            facetChips(listOf(author, genre)).map { it.field } shouldBe
                listOf(FilterField.Genre, FilterField.Author, FilterField.Shelf, FilterField.Tag, FilterField.Series, FilterField.Format)
        }

        it("#232 TC-23: offers Clear only while a facet is set") {
            showsClear(emptyList()) shouldBe false
            showsClear(listOf(FilterClause(FilterField.Tag, FilterMode.Include, listOf("x")))) shouldBe true
        }
    }

    describe("labelling a chip") {

        it("#232 TC-23: reads the first value and how many more follow, as an include or an exclude") {
            checkAll(Exhaustive.enum<FilterMode>(), Exhaustive.of(1, 2, 3)) { mode, count ->
                val values = List(count) { randomString() }
                val chip = FacetChip(FilterField.Genre, FilterClause(FilterField.Genre, mode, values))

                val label = facetLabelOf(chip) { null }

                label.first shouldBe values.first()
                label.more shouldBe count - 1
                label.excluded shouldBe (mode == FilterMode.Exclude)
            }
        }

        it("#232 TC-23: upper-cases a format value") {
            val chip = FacetChip(FilterField.Format, FilterClause(FilterField.Format, FilterMode.Include, listOf("epub")))

            facetLabelOf(chip) { null }.first shouldBe "EPUB"
        }

        it("#232 TC-23: shows a shelf value as the shelf's name") {
            val chip = FacetChip(FilterField.Shelf, FilterClause(FilterField.Shelf, FilterMode.Include, listOf("7")))

            facetLabelOf(chip) { id -> if (id == 7L) "Weekend reads" else null }.first shouldBe "Weekend reads"
        }

        it("#232 TC-23: leaves a shelf value with no known shelf unnamed") {
            val chip = FacetChip(FilterField.Shelf, FilterClause(FilterField.Shelf, FilterMode.Include, listOf("7")))

            facetLabelOf(chip) { null }.first shouldBe null
        }
    }

    describe("applying a facet") {

        val genre = FilterClause(FilterField.Genre, FilterMode.Include, listOf("Fantasy"))
        val tag = FilterClause(FilterField.Tag, FilterMode.Include, listOf("Long"))

        it("#232 TC-23: replaces the clause on the same field") {
            val replaced = genre.copy(values = listOf("Horror"))

            listOf(genre).withClause(replaced) shouldBe listOf(replaced)
        }

        it("#232 TC-23: removes a field's clause when it has no values") {
            listOf(genre).withClause(genre.copy(values = emptyList())) shouldBe emptyList()
        }

        it("#232 TC-23: keeps the other fields' clauses in their order") {
            val replaced = tag.copy(mode = FilterMode.Exclude)

            listOf(genre, tag).withClause(replaced) shouldBe listOf(genre, replaced)
        }
    }

    describe("picking values") {

        it("#232 TC-23: adds a value, then removes it when picked again regardless of case") {
            val added = toggleValue(emptyList(), "Fantasy")

            added shouldBe listOf("Fantasy")
            toggleValue(added, "FANTASY") shouldBe emptyList()
        }

        it("#232 TC-23: refuses the value past the cap") {
            val full = List(MAX_FILTER_VALUES) { randomString() + "-$it" }

            toggleValue(full, "one more") shouldHaveSize MAX_FILTER_VALUES
        }
    }

    describe("the options a shelf's books carry") {

        fun book(
            authors: List<String> = emptyList(),
            series: String? = null,
            genres: List<String> = emptyList(),
            tags: List<String> = emptyList(),
            formats: List<String> = emptyList(),
        ) = randomBook().copy(
            creators = authors.map { Creator(name = it, role = null, fileAs = null, id = null) },
            series = series,
            genres = genres,
            subjects = tags,
            formats = formats,
        )

        it("#232 TC-26: offers only the authors of the shelf's books, with how many books each has") {
            val books = listOf(
                book(authors = listOf("Le Guin")),
                book(authors = listOf("Le Guin", "Pratchett")),
            )

            shelfFacetOptions(FilterField.Author, books) shouldBe listOf(
                FacetOption("Le Guin", "Le Guin", 2),
                FacetOption("Pratchett", "Pratchett", 1),
            )
        }

        it("#232 TC-26: offers nothing for a shelf with no books or a facet its books don't carry") {
            shelfFacetOptions(FilterField.Genre, emptyList()) shouldBe emptyList()
            shelfFacetOptions(FilterField.Tag, listOf(book(authors = listOf("Le Guin")))) shouldBe emptyList()
        }

        it("#232 TC-26: merges values that differ only in case, and counts a book once") {
            val books = listOf(
                book(genres = listOf("Fantasy", "fantasy")),
                book(genres = listOf("FANTASY", "Poetry")),
            )

            shelfFacetOptions(FilterField.Genre, books) shouldBe listOf(
                FacetOption("Fantasy", "Fantasy", 2),
                FacetOption("Poetry", "Poetry", 1),
            )
        }

        it("#232 TC-26: offers series by name, and formats lower-cased with an upper-case label") {
            val books = listOf(book(series = "Earthsea", formats = listOf("EPUB")), book(formats = listOf("epub", "m4b")))

            shelfFacetOptions(FilterField.Series, books) shouldBe listOf(FacetOption("Earthsea", "Earthsea", 1))
            shelfFacetOptions(FilterField.Format, books) shouldBe listOf(
                FacetOption("epub", "EPUB", 2),
                FacetOption("m4b", "M4B", 1),
            )
        }

        it("#232 TC-26: leaves the shelf facet to the shelves read") {
            shelfFacetOptions(FilterField.Shelf, listOf(book(authors = listOf("Le Guin")))) shouldBe emptyList()
        }
    }
})
