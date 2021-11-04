package com.github.sawafrolov.mflix;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;

public class Migrator {

    /**
     * Creates and UpdateOneModel object for each Document that contains an "imdb.rating" field of
     * non-numerical type into a parsable
     *
     * @param doc - Document object to be updated
     * @return UpdateOneModel operation response object
     */
    private static UpdateOneModel<Document> transformRating(Document doc) {
        try {
            String imdbRating = doc.get("imdb", Document.class).getString("rating");
            if (imdbRating == null) {
                return null;
            }

            int rating = 0;
            if (!"".equals(imdbRating)) {
                rating = Integer.parseInt(imdbRating);
            }

            return new UpdateOneModel<>(
                    eq("_id", doc.getObjectId("_id")),
                    set("imdb.rating", rating)
            );
        } catch (NumberFormatException e) {
            System.out.println(
                    MessageFormat.format(
                            "Could not parse {0} into " + "number: {1}", doc.get("imdb.rating", e.getMessage())));
        }
        return null;
    }

    /**
     * Creates an UpdateOneModel for each Document object field `lastupdated` of type string into an
     * update $set to Date type. db.movies.update({_id: doc._id}, {$set: {lastupdated:
     * ISODate(doc.lastupdated)}})
     *
     * @param doc - Document object to get the date transformation applied to
     * @return UpdateOneModel object or null if no change is required.
     */
    private static UpdateOneModel<Document> transformDates(Document doc, DateFormat dateFormat) {

        String lastUpdated = doc.getString("lastupdated");

        try {
            if (lastUpdated != null) {
                return new UpdateOneModel<>(
                        eq("_id", doc.getObjectId("_id")),
                        set("lastupdated", dateFormat.parse(lastUpdated))
                );
            }

        } catch (ParseException e) {
            System.out.println(
                    MessageFormat.format(
                            "String date {0} cannot be parsed using {1} " + "format: {2}",
                            lastUpdated, dateFormat, e.getMessage()));
        }

        return null;
    }

    /**
     * Migration script main class. This should be executed within the IDE!
     *
     * @param args is a set of system arguments that can be ignored.
     */
    public static void main(String[] args) {

        // Instantiate database and collection objects
        System.out.println("Dataset cleanup migration");
        String mongoUri = "mongodb+srv://m001-student:m001-mongodb-basics@sandbox.i0pmw.mongodb.net";
        MongoDatabase mflix = MongoClients.create(mongoUri).getDatabase("sample_mflix");
        MongoCollection<Document> movies = mflix.getCollection("movies");

        // Apply lastupdated to ISO Date
        Bson dateStringFilter = and(
                exists("lastupdated"),
                type("lastupdated", "string")
        );
        String datePattern = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat dateFormat = new SimpleDateFormat(datePattern);
        List<WriteModel<Document>> bulkWrites = new ArrayList<>();
        for (Document doc : movies.find(dateStringFilter)) {
            WriteModel<Document> updateDate = transformDates(doc, dateFormat);
            if (updateDate != null) {
                bulkWrites.add(updateDate);
            }
        }

        // Apply IMDB rating to integer
        Bson ratingStringFilter = and(
                exists("imdb.rating"),
                type("imdb.rating", "string")
        );
        for (Document doc : movies.find(ratingStringFilter)) {
            WriteModel<Document> updateRating = transformRating(doc);
            if (updateRating != null) {
                bulkWrites.add(updateRating);
            }
        }

        // Execute the bulk update
        BulkWriteOptions bulkWriteOptions = new BulkWriteOptions().ordered(false);
        if (bulkWrites.isEmpty()) {
            System.out.println("Nothing to update!");
            System.exit(0);
        }
        BulkWriteResult bulkResult = movies.bulkWrite(bulkWrites, bulkWriteOptions);
        System.out.println(
                MessageFormat.format("Updated {0} documents", bulkResult.getModifiedCount())
        );
    }
}
