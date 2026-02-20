package com.example.traveltool.data

/**
 * Static airport database with IATA code, name, and city.
 * Provides search by city name, airport name, or IATA code.
 */
object AirportDatabase {

    data class Airport(
        val iata: String,
        val name: String,
        val city: String,
        val country: String,
    ) {
        /** Display text shown in search results and chips. */
        val displayName: String get() = "$name ($iata)"
        /** Short label for the ticket card. */
        val shortLabel: String get() = "$city – $iata"
    }

    /** Search airports by city, name, or IATA code (case-insensitive, min 2 chars). */
    fun search(query: String): List<Airport> {
        if (query.length < 2) return emptyList()
        val q = query.trim().lowercase()
        return airports.filter { a ->
            a.city.lowercase().contains(q) ||
            a.name.lowercase().contains(q) ||
            a.iata.lowercase().startsWith(q)
        }.take(8)
    }

    fun findByCode(iata: String): Airport? = airports.find { it.iata.equals(iata, ignoreCase = true) }

    // ── Airport list (major world airports) ─────────────────
    private val airports = listOf(
        // Europe
        Airport("BUD", "Budapest Liszt Ferenc International Airport", "Budapest", "Hungary"),
        Airport("VIE", "Vienna International Airport", "Vienna", "Austria"),
        Airport("PRG", "Václav Havel Airport Prague", "Prague", "Czech Republic"),
        Airport("WAW", "Warsaw Chopin Airport", "Warsaw", "Poland"),
        Airport("KRK", "John Paul II International Airport", "Kraków", "Poland"),
        Airport("BTS", "M. R. Štefánik Airport", "Bratislava", "Slovakia"),
        Airport("ZAG", "Franjo Tuđman Airport", "Zagreb", "Croatia"),
        Airport("SPU", "Split Airport", "Split", "Croatia"),
        Airport("DBV", "Dubrovnik Airport", "Dubrovnik", "Croatia"),
        Airport("BEG", "Belgrade Nikola Tesla Airport", "Belgrade", "Serbia"),
        Airport("OTP", "Henri Coandă International Airport", "Bucharest", "Romania"),
        Airport("CLJ", "Cluj-Napoca International Airport", "Cluj-Napoca", "Romania"),
        Airport("SOF", "Sofia Airport", "Sofia", "Bulgaria"),
        Airport("ATH", "Athens Eleftherios Venizelos International Airport", "Athens", "Greece"),
        Airport("SKG", "Thessaloniki Macedonia International Airport", "Thessaloniki", "Greece"),
        Airport("HER", "Heraklion International Airport", "Heraklion", "Greece"),
        Airport("IST", "Istanbul Airport", "Istanbul", "Turkey"),
        Airport("SAW", "Istanbul Sabiha Gökçen International Airport", "Istanbul", "Turkey"),
        Airport("AYT", "Antalya Airport", "Antalya", "Turkey"),
        Airport("LHR", "London Heathrow Airport", "London", "United Kingdom"),
        Airport("LGW", "London Gatwick Airport", "London", "United Kingdom"),
        Airport("STN", "London Stansted Airport", "London", "United Kingdom"),
        Airport("LTN", "London Luton Airport", "London", "United Kingdom"),
        Airport("LCY", "London City Airport", "London", "United Kingdom"),
        Airport("CDG", "Paris Charles de Gaulle Airport", "Paris", "France"),
        Airport("ORY", "Paris Orly Airport", "Paris", "France"),
        Airport("BVA", "Paris Beauvais-Tillé Airport", "Paris", "France"),
        Airport("AMS", "Amsterdam Schiphol Airport", "Amsterdam", "Netherlands"),
        Airport("BRU", "Brussels Airport", "Brussels", "Belgium"),
        Airport("CRL", "Brussels South Charleroi Airport", "Brussels", "Belgium"),
        Airport("FRA", "Frankfurt Airport", "Frankfurt", "Germany"),
        Airport("MUC", "Munich Airport", "Munich", "Germany"),
        Airport("BER", "Berlin Brandenburg Airport", "Berlin", "Germany"),
        Airport("DUS", "Düsseldorf Airport", "Düsseldorf", "Germany"),
        Airport("HAM", "Hamburg Airport", "Hamburg", "Germany"),
        Airport("CGN", "Cologne Bonn Airport", "Cologne", "Germany"),
        Airport("STR", "Stuttgart Airport", "Stuttgart", "Germany"),
        Airport("ZRH", "Zurich Airport", "Zurich", "Switzerland"),
        Airport("GVA", "Geneva Airport", "Geneva", "Switzerland"),
        Airport("BSL", "EuroAirport Basel-Mulhouse-Freiburg", "Basel", "Switzerland"),
        Airport("MXP", "Milan Malpensa Airport", "Milan", "Italy"),
        Airport("LIN", "Milan Linate Airport", "Milan", "Italy"),
        Airport("FCO", "Rome Fiumicino Airport", "Rome", "Italy"),
        Airport("CIA", "Rome Ciampino Airport", "Rome", "Italy"),
        Airport("VCE", "Venice Marco Polo Airport", "Venice", "Italy"),
        Airport("NAP", "Naples International Airport", "Naples", "Italy"),
        Airport("BGY", "Milan Bergamo Airport", "Bergamo", "Italy"),
        Airport("MAD", "Adolfo Suárez Madrid–Barajas Airport", "Madrid", "Spain"),
        Airport("BCN", "Barcelona–El Prat Airport", "Barcelona", "Spain"),
        Airport("PMI", "Palma de Mallorca Airport", "Palma de Mallorca", "Spain"),
        Airport("AGP", "Málaga Airport", "Málaga", "Spain"),
        Airport("ALC", "Alicante Airport", "Alicante", "Spain"),
        Airport("VLC", "Valencia Airport", "Valencia", "Spain"),
        Airport("LIS", "Lisbon Humberto Delgado Airport", "Lisbon", "Portugal"),
        Airport("OPO", "Porto Airport", "Porto", "Portugal"),
        Airport("FAO", "Faro Airport", "Faro", "Portugal"),
        Airport("DUB", "Dublin Airport", "Dublin", "Ireland"),
        Airport("CPH", "Copenhagen Airport", "Copenhagen", "Denmark"),
        Airport("ARN", "Stockholm Arlanda Airport", "Stockholm", "Sweden"),
        Airport("OSL", "Oslo Gardermoen Airport", "Oslo", "Norway"),
        Airport("HEL", "Helsinki-Vantaa Airport", "Helsinki", "Finland"),
        Airport("RIX", "Riga International Airport", "Riga", "Latvia"),
        Airport("VNO", "Vilnius Airport", "Vilnius", "Lithuania"),
        Airport("TLL", "Tallinn Airport", "Tallinn", "Estonia"),
        Airport("EDI", "Edinburgh Airport", "Edinburgh", "United Kingdom"),
        Airport("MAN", "Manchester Airport", "Manchester", "United Kingdom"),
        Airport("BHX", "Birmingham Airport", "Birmingham", "United Kingdom"),

        // North America
        Airport("JFK", "John F. Kennedy International Airport", "New York", "United States"),
        Airport("EWR", "Newark Liberty International Airport", "New York", "United States"),
        Airport("LGA", "LaGuardia Airport", "New York", "United States"),
        Airport("LAX", "Los Angeles International Airport", "Los Angeles", "United States"),
        Airport("ORD", "O'Hare International Airport", "Chicago", "United States"),
        Airport("MDW", "Chicago Midway International Airport", "Chicago", "United States"),
        Airport("SFO", "San Francisco International Airport", "San Francisco", "United States"),
        Airport("MIA", "Miami International Airport", "Miami", "United States"),
        Airport("FLL", "Fort Lauderdale–Hollywood International Airport", "Fort Lauderdale", "United States"),
        Airport("ATL", "Hartsfield–Jackson Atlanta International Airport", "Atlanta", "United States"),
        Airport("DFW", "Dallas/Fort Worth International Airport", "Dallas", "United States"),
        Airport("DEN", "Denver International Airport", "Denver", "United States"),
        Airport("SEA", "Seattle–Tacoma International Airport", "Seattle", "United States"),
        Airport("BOS", "Boston Logan International Airport", "Boston", "United States"),
        Airport("IAD", "Washington Dulles International Airport", "Washington", "United States"),
        Airport("DCA", "Ronald Reagan Washington National Airport", "Washington", "United States"),
        Airport("PHX", "Phoenix Sky Harbor International Airport", "Phoenix", "United States"),
        Airport("LAS", "Harry Reid International Airport", "Las Vegas", "United States"),
        Airport("MCO", "Orlando International Airport", "Orlando", "United States"),
        Airport("MSP", "Minneapolis–Saint Paul International Airport", "Minneapolis", "United States"),
        Airport("DTW", "Detroit Metropolitan Wayne County Airport", "Detroit", "United States"),
        Airport("PHL", "Philadelphia International Airport", "Philadelphia", "United States"),
        Airport("CLT", "Charlotte Douglas International Airport", "Charlotte", "United States"),
        Airport("SAN", "San Diego International Airport", "San Diego", "United States"),
        Airport("HNL", "Daniel K. Inouye International Airport", "Honolulu", "United States"),
        Airport("YYZ", "Toronto Pearson International Airport", "Toronto", "Canada"),
        Airport("YVR", "Vancouver International Airport", "Vancouver", "Canada"),
        Airport("YUL", "Montréal–Trudeau International Airport", "Montreal", "Canada"),
        Airport("YOW", "Ottawa Macdonald–Cartier International Airport", "Ottawa", "Canada"),
        Airport("YYC", "Calgary International Airport", "Calgary", "Canada"),
        Airport("MEX", "Mexico City International Airport", "Mexico City", "Mexico"),
        Airport("CUN", "Cancún International Airport", "Cancún", "Mexico"),

        // Asia
        Airport("NRT", "Narita International Airport", "Tokyo", "Japan"),
        Airport("HND", "Tokyo Haneda Airport", "Tokyo", "Japan"),
        Airport("KIX", "Kansai International Airport", "Osaka", "Japan"),
        Airport("ICN", "Incheon International Airport", "Seoul", "South Korea"),
        Airport("PEK", "Beijing Capital International Airport", "Beijing", "China"),
        Airport("PKX", "Beijing Daxing International Airport", "Beijing", "China"),
        Airport("PVG", "Shanghai Pudong International Airport", "Shanghai", "China"),
        Airport("HKG", "Hong Kong International Airport", "Hong Kong", "China"),
        Airport("SIN", "Singapore Changi Airport", "Singapore", "Singapore"),
        Airport("BKK", "Suvarnabhumi Airport", "Bangkok", "Thailand"),
        Airport("KUL", "Kuala Lumpur International Airport", "Kuala Lumpur", "Malaysia"),
        Airport("CGK", "Soekarno–Hatta International Airport", "Jakarta", "Indonesia"),
        Airport("DPS", "Ngurah Rai International Airport", "Bali", "Indonesia"),
        Airport("DEL", "Indira Gandhi International Airport", "Delhi", "India"),
        Airport("BOM", "Chhatrapati Shivaji Maharaj International Airport", "Mumbai", "India"),
        Airport("DXB", "Dubai International Airport", "Dubai", "United Arab Emirates"),
        Airport("AUH", "Abu Dhabi International Airport", "Abu Dhabi", "United Arab Emirates"),
        Airport("DOH", "Hamad International Airport", "Doha", "Qatar"),
        Airport("TLV", "Ben Gurion Airport", "Tel Aviv", "Israel"),

        // Africa
        Airport("JNB", "O.R. Tambo International Airport", "Johannesburg", "South Africa"),
        Airport("CPT", "Cape Town International Airport", "Cape Town", "South Africa"),
        Airport("CAI", "Cairo International Airport", "Cairo", "Egypt"),
        Airport("CMN", "Mohammed V International Airport", "Casablanca", "Morocco"),
        Airport("NBO", "Jomo Kenyatta International Airport", "Nairobi", "Kenya"),
        Airport("ADD", "Addis Ababa Bole International Airport", "Addis Ababa", "Ethiopia"),

        // South America
        Airport("GRU", "São Paulo/Guarulhos International Airport", "São Paulo", "Brazil"),
        Airport("GIG", "Rio de Janeiro/Galeão International Airport", "Rio de Janeiro", "Brazil"),
        Airport("EZE", "Ministro Pistarini International Airport", "Buenos Aires", "Argentina"),
        Airport("SCL", "Arturo Merino Benítez International Airport", "Santiago", "Chile"),
        Airport("BOG", "El Dorado International Airport", "Bogotá", "Colombia"),
        Airport("LIM", "Jorge Chávez International Airport", "Lima", "Peru"),

        // Oceania
        Airport("SYD", "Sydney Kingsford Smith Airport", "Sydney", "Australia"),
        Airport("MEL", "Melbourne Airport", "Melbourne", "Australia"),
        Airport("BNE", "Brisbane Airport", "Brisbane", "Australia"),
        Airport("AKL", "Auckland Airport", "Auckland", "New Zealand"),
    )
}
