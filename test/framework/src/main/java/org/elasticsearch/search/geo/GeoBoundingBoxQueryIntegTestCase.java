/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.geo;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.query.GeoValidationMethod;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.geoBoundingBoxQuery;
import static org.elasticsearch.index.query.QueryBuilders.geoDistanceQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

public abstract class GeoBoundingBoxQueryIntegTestCase extends ESIntegTestCase {

    /**
     * Get the mapping for this test. It should contain a field called `location' that
     * supports GeoBoundingBox queries.
     */
    public abstract XContentBuilder getMapping() throws IOException;

    /**
     * Provides a supported version when the mapping was created.
     */
    public abstract IndexVersion randomSupportedVersion() throws IOException;

    @Override
    protected boolean forbidPrivateIndexSettings() {
        return false;
    }

    public void testSimpleBoundingBoxTest() throws Exception {
        Settings settings = Settings.builder().put(IndexMetadata.SETTING_VERSION_CREATED, randomSupportedVersion()).build();
        XContentBuilder xContentBuilder = getMapping();
        assertAcked(prepareCreate("test").setSettings(settings).setMapping(xContentBuilder));
        ensureGreen();

        client().prepareIndex("test")
            .setId("1")
            .setSource(jsonBuilder().startObject().field("name", "New York").field("location", "POINT(-74.0059731 40.7143528)").endObject())
            .get();

        // to NY: 5.286 km
        client().prepareIndex("test")
            .setId("2")
            .setSource(
                jsonBuilder().startObject().field("name", "Times Square").field("location", "POINT(-73.9844722 40.759011)").endObject()
            )
            .get();

        // to NY: 0.4621 km
        client().prepareIndex("test")
            .setId("3")
            .setSource(jsonBuilder().startObject().field("name", "Tribeca").field("location", "POINT(-74.007819 40.718266)").endObject())
            .get();

        // to NY: 1.055 km
        client().prepareIndex("test")
            .setId("4")
            .setSource(
                jsonBuilder().startObject().field("name", "Wall Street").field("location", "POINT(-74.0088305 40.7051157)").endObject()
            )
            .get();

        // to NY: 1.258 km
        client().prepareIndex("test")
            .setId("5")
            .setSource(jsonBuilder().startObject().field("name", "Soho").field("location", "POINT(-74 40.7247222)").endObject())
            .get();

        // to NY: 2.029 km
        client().prepareIndex("test")
            .setId("6")
            .setSource(
                jsonBuilder().startObject().field("name", "Greenwich Village").field("location", "POINT(-73.9962255 40.731033)").endObject()
            )
            .get();

        // to NY: 8.572 km
        client().prepareIndex("test")
            .setId("7")
            .setSource(jsonBuilder().startObject().field("name", "Brooklyn").field("location", "POINT(-73.95 40.65)").endObject())
            .get();

        client().admin().indices().prepareRefresh().get();

        SearchResponse searchResponse = prepareSearch() // from NY
            .setQuery(geoBoundingBoxQuery("location").setCorners(40.73, -74.1, 40.717, -73.99))
            .get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(2L));
        assertThat(searchResponse.getHits().getHits().length, equalTo(2));
        for (SearchHit hit : searchResponse.getHits()) {
            assertThat(hit.getId(), anyOf(equalTo("1"), equalTo("3"), equalTo("5")));
        }

        searchResponse = prepareSearch() // from NY
            .setQuery(geoBoundingBoxQuery("location").setCorners(40.73, -74.1, 40.717, -73.99))
            .get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(2L));
        assertThat(searchResponse.getHits().getHits().length, equalTo(2));
        for (SearchHit hit : searchResponse.getHits()) {
            assertThat(hit.getId(), anyOf(equalTo("1"), equalTo("3"), equalTo("5")));
        }

        searchResponse = prepareSearch() // top == bottom && left == right
            .setQuery(geoBoundingBoxQuery("location").setCorners(40.7143528, -74.0059731, 40.7143528, -74.0059731))
            .get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        assertThat(searchResponse.getHits().getHits().length, equalTo(1));
        for (SearchHit hit : searchResponse.getHits()) {
            assertThat(hit.getId(), equalTo("1"));
        }

        searchResponse = prepareSearch() // top == bottom
            .setQuery(geoBoundingBoxQuery("location").setCorners(40.759011, -74.00009, 40.759011, -73.0059731))
            .get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        assertThat(searchResponse.getHits().getHits().length, equalTo(1));
        for (SearchHit hit : searchResponse.getHits()) {
            assertThat(hit.getId(), equalTo("2"));
        }

        searchResponse = prepareSearch() // left == right
            .setQuery(geoBoundingBoxQuery("location").setCorners(41.8, -73.9844722, 40.7, -73.9844722))
            .get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        assertThat(searchResponse.getHits().getHits().length, equalTo(1));
        for (SearchHit hit : searchResponse.getHits()) {
            assertThat(hit.getId(), equalTo("2"));
        }

        // Distance query
        searchResponse = prepareSearch() // from NY
            .setQuery(geoDistanceQuery("location").point(40.5, -73.9).distance(25, DistanceUnit.KILOMETERS))
            .get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(2L));
        assertThat(searchResponse.getHits().getHits().length, equalTo(2));
        for (SearchHit hit : searchResponse.getHits()) {
            assertThat(hit.getId(), anyOf(equalTo("7"), equalTo("4")));
        }
    }

    public void testLimit2BoundingBox() throws Exception {
        Settings settings = Settings.builder().put(IndexMetadata.SETTING_VERSION_CREATED, randomSupportedVersion()).build();
        XContentBuilder xContentBuilder = getMapping();
        assertAcked(prepareCreate("test").setSettings(settings).setMapping(xContentBuilder));
        ensureGreen();

        client().prepareIndex("test")
            .setId("1")
            .setSource(
                jsonBuilder().startObject()
                    .field("userid", 880)
                    .field("title", "Place in Stockholm")
                    .field("location", "POINT(59.328355000000002 18.036842)")
                    .endObject()
            )
            .setRefreshPolicy(IMMEDIATE)
            .get();

        client().prepareIndex("test")
            .setId("2")
            .setSource(
                jsonBuilder().startObject()
                    .field("userid", 534)
                    .field("title", "Place in Montreal")
                    .field("location", "POINT(-73.570986000000005 45.509526999999999)")
                    .endObject()
            )
            .setRefreshPolicy(IMMEDIATE)
            .get();

        SearchResponse searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 880))
                .filter(geoBoundingBoxQuery("location").setCorners(74.579421999999994, 143.5, -66.668903999999998, 113.96875))
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 880))
                .filter(geoBoundingBoxQuery("location").setCorners(74.579421999999994, 143.5, -66.668903999999998, 113.96875))
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));

        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 534))
                .filter(geoBoundingBoxQuery("location").setCorners(74.579421999999994, 143.5, -66.668903999999998, 113.96875))
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 534))
                .filter(geoBoundingBoxQuery("location").setCorners(74.579421999999994, 143.5, -66.668903999999998, 113.96875))
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));

        // top == bottom && left == right
        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 880))
                .filter(geoBoundingBoxQuery("location").setCorners(18.036842, 59.328355000000002, 18.036842, 59.328355000000002))
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 534))
                .filter(
                    geoBoundingBoxQuery("location").setCorners(
                        45.509526999999999,
                        -73.570986000000005,
                        45.509526999999999,
                        -73.570986000000005
                    )
                )
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));

        // top == bottom
        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 880))
                .filter(geoBoundingBoxQuery("location").setCorners(18.036842, 143.5, 18.036842, 113.96875))
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 534))
                .filter(geoBoundingBoxQuery("location").setCorners(45.509526999999999, 143.5, 45.509526999999999, 113.96875))
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));

        // left == right
        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 880))
                .filter(
                    geoBoundingBoxQuery("location").setCorners(
                        74.579421999999994,
                        59.328355000000002,
                        -66.668903999999998,
                        59.328355000000002
                    )
                )
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 534))
                .filter(
                    geoBoundingBoxQuery("location").setCorners(
                        74.579421999999994,
                        -73.570986000000005,
                        -66.668903999999998,
                        -73.570986000000005
                    )
                )
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));

        // Distance query
        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 880))
                .filter(geoDistanceQuery("location").point(20, 60.0).distance(500, DistanceUnit.MILES))
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));

        searchResponse = prepareSearch().setQuery(
            boolQuery().must(termQuery("userid", 534))
                .filter(geoDistanceQuery("location").point(45.0, -73.0).distance(500, DistanceUnit.MILES))
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
    }

    public void testCompleteLonRange() throws Exception {
        Settings settings = Settings.builder().put(IndexMetadata.SETTING_VERSION_CREATED, randomSupportedVersion()).build();
        XContentBuilder xContentBuilder = getMapping();
        assertAcked(prepareCreate("test").setSettings(settings).setMapping(xContentBuilder));
        ensureGreen();

        client().prepareIndex("test")
            .setId("1")
            .setSource(
                jsonBuilder().startObject()
                    .field("userid", 880)
                    .field("title", "Place in Stockholm")
                    .field("location", "POINT(18.036842 59.328355000000002)")
                    .endObject()
            )
            .setRefreshPolicy(IMMEDIATE)
            .get();

        client().prepareIndex("test")
            .setId("2")
            .setSource(
                jsonBuilder().startObject()
                    .field("userid", 534)
                    .field("title", "Place in Montreal")
                    .field("location", "POINT(-73.570986000000005 45.509526999999999)")
                    .endObject()
            )
            .setRefreshPolicy(IMMEDIATE)
            .get();

        SearchResponse searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE).setCorners(50, -180, -50, 180)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE).setCorners(50, -180, -50, 180)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE).setCorners(90, -180, -90, 180)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(2L));
        searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE).setCorners(90, -180, -90, 180)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(2L));

        searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE).setCorners(50, 0, -50, 360)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE).setCorners(50, 0, -50, 360)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE).setCorners(90, 0, -90, 360)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(2L));
        searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE).setCorners(90, 0, -90, 360)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(2L));

        // top == bottom
        searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE)
                .setCorners(59.328355000000002, 0, 59.328355000000002, 360)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        searchResponse = prepareSearch().setQuery(
            geoBoundingBoxQuery("location").setValidationMethod(GeoValidationMethod.COERCE)
                .setCorners(59.328355000000002, -180, 59.328355000000002, 180)
        ).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));

        // Distance query
        searchResponse = prepareSearch().setQuery(geoDistanceQuery("location").point(60.0, -20.0).distance(1800, DistanceUnit.MILES)).get();
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
    }
}
