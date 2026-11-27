package com.marquee.launcher.space

import android.content.Context
import kotlin.random.Random

class Photo(val url: String, val title: String, val subtitle: String, val credit: String)

/**
 * Hand-picked Webb, Hubble and Space Station photos. ESA/Webb and ESA/Hubble images are CC BY 4.0
 * (credited on screen); Space Station photos are NASA's. The "wallpaper4" size is 1920x1200, right for a TV.
 */
object SpacePhotos {
    private const val WEBB = "James Webb Space Telescope"
    private const val HUBBLE = "Hubble Space Telescope"
    private const val ISS = "From the International Space Station"

    private fun webb(id: String, title: String, what: String, credit: String = "NASA, ESA, CSA, STScI") =
        Photo("https://cdn.esawebb.org/archives/images/wallpaper4/$id.jpg", title, "$what · $WEBB", credit)

    private fun hubble(id: String, title: String, what: String, credit: String = "NASA, ESA") =
        Photo("https://cdn.esahubble.org/archives/images/wallpaper4/$id.jpg", title, "$what · $HUBBLE", credit)

    private fun iss(id: String, title: String, what: String = ISS) =
        Photo("https://images-assets.nasa.gov/image/$id/$id~large.jpg", title, what, "NASA")

    val all: List<Photo> = listOf(
        webb("weic2205a", "Cosmic Cliffs", "Carina Nebula"),
        webb("weic2208a", "Stephan's Quintet", "Galaxy group in Pegasus"),
        webb("weic2209a", "Webb's First Deep Field", "Galaxy cluster SMACS 0723"),
        webb("weic2211a", "Cartwheel Galaxy", "Ring galaxy in Sculptor"),
        webb("weic2212a", "Tarantula Nebula", "Large Magellanic Cloud"),
        webb("weic2216a", "Pillars of Creation", "Eagle Nebula", "NASA, ESA, CSA, STScI; J. DePasquale, A. Koekemoer, A. Pagan"),
        webb("weic2307a", "Wolf-Rayet 124", "A dying star shedding its layers", "NASA, ESA, CSA, STScI, Webb ERO Production Team"),
        webb("weic2316a", "Rho Ophiuchi", "The nearest star-forming region to Earth", "NASA, ESA, CSA, STScI, K. Pontoppidan, A. Pagan"),
        webb("weic2319a", "Herbig-Haro 46/47", "A pair of newborn stars", "NASA, ESA, CSA, J. DePasquale (STScI)"),
        webb("weic2326a", "Crab Nebula", "Supernova remnant in Taurus", "NASA, ESA, CSA, STScI, T. Temim"),
        webb("weic2328a", "Sagittarius C", "Near the center of the Milky Way", "NASA, ESA, CSA, STScI, S. Crowe"),
        webb("weic2411a", "Horsehead Nebula", "Orion", "ESA/Webb, NASA, CSA, K. Misselt, A. Abergel"),
        webb("weic2407a", "NGC 604", "Star-forming region in the Triangulum Galaxy"),
        webb("weic2423a", "Arp 107", "Colliding galaxies"),
        webb("weic2426a", "IC 2163 and NGC 2207", "Two galaxies brushing past each other"),
        webb("weic2427a", "Sombrero Galaxy", "In mid-infrared"),
        webb("potm2409a", "Westerlund 1", "The Milky Way's most massive young star cluster", "ESA/Webb, NASA & CSA, M. Zamani, M. G. Guarcello"),
        webb("weic2425a", "NGC 602", "Star cluster in the Small Magellanic Cloud", "ESA/Webb, NASA & CSA, P. Zeidler, E. Sabbi, A. Nota, M. Zamani"),
        webb("weic2520a", "Sagittarius B2", "Giant star-forming cloud near the galactic center", "NASA, ESA, CSA, STScI, A. Ginsburg, N. Budaiev, T. Yoo"),
        webb("weic2601a", "Helix Nebula", "The glowing shell of a dying star", "NASA, ESA, CSA, STScI, A. Pagan"),
        webb("weic2608a", "Whirlpool Galaxy", "Star-forming regions in M51", "ESA/Webb, NASA & CSA, A. Pedrini, A. Adamo, FEAST JWST team"),
        webb("weic2612a", "Cigar Galaxy", "Starburst galaxy M82, with Hubble", "NASA, ESA, CSA, A. Smercina, T. Williams, A. Pagan"),
        webb("potm2208a", "Phantom Galaxy", "Spiral galaxy M74 in Pisces", "ESA/Webb, NASA & CSA, J. Lee and the PHANGS-JWST Team"),
        webb("jupiter-auroras1", "Jupiter", "Auroras and hazes in infrared", "NASA, ESA, Jupiter ERS Team; Judy Schmidt"),
        webb("potm2610a", "A Nebulous Cauldron", "Young stars brewing in gas and dust", "ESA/Webb, NASA & CSA, E. Sabbi, M. Zamani"),
        webb("weic2611a", "Terzan 5", "Ancient star cluster in the Milky Way's bulge, with Hubble", "NASA, ESA, CSA, STScI, G. Zullo, F. R. Ferraro"),
        webb("potm2511a", "A Dance of Dwarf Galaxies", "Interacting dwarf galaxies", "ESA/Webb, NASA & CSA, A. Adamo, G. Bortolini, FEAST JWST team"),

        hubble("heic2007a", "Tapestry of Blazing Starbirth", "NGC 2014 and NGC 2020", "NASA, ESA, STScI"),
        hubble("heic1509a", "Westerlund 2", "Hubble's 25th anniversary image", "NASA, ESA, Hubble Heritage Team, A. Nota"),
        hubble("opo0328a", "Sombrero Galaxy", "Messier 104", "NASA/ESA, Hubble Heritage Team"),
        hubble("heic1917a", "Saturn", "Summer 2019", "NASA, ESA, A. Simon, M. H. Wong"),
        hubble("heic0506a", "Whirlpool Galaxy", "M51 and its companion", "NASA, ESA, S. Beckwith, Hubble Heritage Team"),
        hubble("heic0206c", "Cone Nebula", "A star-forming pillar", "NASA, H. Ford, ACS Science Team, ESA"),
        hubble("opo0501a", "NGC 1300", "Barred spiral galaxy", "NASA, ESA, Hubble Heritage Team"),
        hubble("opo0006a", "Carina Nebula", "Light and shadow", "NASA/ESA, Hubble Heritage Team"),
        hubble("heic2105a", "AG Carinae", "One of the brightest stars in our galaxy", "NASA, ESA, STScI"),
        hubble("heic0602a", "Pinwheel Galaxy", "Messier 101", "ESA & NASA"),
        hubble("opo9941a", "NGC 2207 and IC 2163", "A grazing encounter", "NASA/ESA, Hubble Heritage Team"),
        hubble("heic0411a", "N11", "Star birth in the Large Magellanic Cloud", "NASA/ESA, Hubble Heritage Team"),
        hubble("heic1520a", "Veil Nebula", "Supernova remnant in Cygnus", "NASA, ESA, Hubble Heritage Team"),
        hubble("heic1502a", "Andromeda Galaxy", "Hubble's sharpest view", "NASA, ESA, J. Dalcanton, B. F. Williams, L. C. Johnson"),
        hubble("opo1438b", "Bode's Galaxy", "Spiral galaxy M81", "NASA, ESA, A. Zezas, A. Filippenko"),
        hubble("heic0910e", "Carina Nebula", "Pillars of gas and dust", "NASA, ESA, Hubble SM4 ERO Team"),
        hubble("heic0601a", "Orion Nebula", "Hubble's sharpest view", "NASA, ESA, M. Robberto, Hubble Orion Treasury Team"),
        hubble("heic0515a", "Crab Nebula", "Supernova remnant in Taurus", "NASA, ESA, A. Loll, J. Hester"),
        hubble("heic1608a", "Bubble Nebula", "Cassiopeia", "NASA, ESA, Hubble Heritage Team"),
        hubble("heic1007a", "Mystic Mountain", "Carina Nebula", "NASA, ESA, M. Livio, Hubble 20th Anniversary Team"),
        hubble("heic2017a", "Jupiter and Europa", "August 2020", "NASA, ESA, A. Simon, M. H. Wong"),
        hubble("potw1345a", "Antennae Galaxies", "Colliding galaxies", "ESA/Hubble & NASA"),
        hubble("heic1310a", "Ring Nebula", "Messier 57", "NASA, ESA, C. R. O'Dell"),
        hubble("heic0707a", "Star Birth in the Extreme", "A giant stellar nursery", "NASA, ESA, N. Smith, Hubble Heritage Team"),
        hubble("heic0109a", "Red Spider Nebula", "Planetary nebula in Sagittarius", "ESA, G. Mellema"),
        hubble("potw2006a", "Open Arms", "Spiral galaxy", "ESA/Hubble & NASA, A. Bellini"),

        iss("iss058e005282", "Aurora", "Over the night side of Earth, from the Space Station"),
        iss("iss043e059141", "Aurora Borealis"),
        iss("iss071e256593", "The Milky Way", "Behind a SpaceX Dragon docked to the Space Station"),
        iss("iss071e449837", "The Bahamas"),
        iss("iss058e002206", "Cuba and the Bahamas"),
    )

    /**
     * A shuffled order that carries over between screensaver runs, so every photo shows
     * before any repeats.
     */
    class Playlist(context: Context) {
        private val prefs = context.getSharedPreferences("marquee", Context.MODE_PRIVATE)
        private var seed = prefs.getLong("space_seed", 0L).takeIf { it != 0L } ?: newSeed()
        private var order = all.shuffled(Random(seed))
        private var pos = prefs.getInt("space_pos", 0).coerceIn(0, all.size)

        fun next(): Photo {
            if (pos >= order.size) {
                seed = newSeed()
                order = all.shuffled(Random(seed))
                pos = 0
            }
            val p = order[pos++]
            prefs.edit().putInt("space_pos", pos).apply()
            return p
        }

        fun peek(): Photo = if (pos < order.size) order[pos] else order.first()

        private fun newSeed(): Long {
            val s = Random.nextLong().takeIf { it != 0L } ?: 1L
            prefs.edit().putLong("space_seed", s).putInt("space_pos", 0).apply()
            return s
        }
    }
}
