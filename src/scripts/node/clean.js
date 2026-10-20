const fsp = require('fs/promises');

const csv = require('csvtojson');
const axios = require('axios').default;
const yargs = require('yargs/yargs');
const { hideBin } = require('yargs/helpers');

const LOG_FILE = `./clean-${Date.now()}.log`;

const log = async (message, ...optionalParams) => {
  await fsp.appendFile(LOG_FILE, `${message}\n`);
  console.log(message, ...optionalParams);
};

// Parameter parsing & check
const args = yargs(hideBin(process.argv)).argv;

async function deleteImage(species, image, cookie) {
  // Construct the API url
  const URL = `https://${
    args.dev ? 'profiles-dev' : 'profiles'
  }.ala.org.au/opus/${
    args.opusId || 'mangrovewatch'
  }/profile/${encodeURIComponent(species)}/image/${image}/delete?type=PRIVATE`;

  let response;
  try {
    response = await axios.delete(URL, {
      headers: {
        cookie,
      },
    });
  } catch (error) {
    if (error?.response?.status === 403) {
      await log('Cookie is invalid!');
      throw new Error('Cookie is invalid!');
    }

    await log(
      `Image deletion failed (${error?.response?.status || 'Unknown Error'})`
    );
    await log(JSON.stringify(image, null, 2));
  }

  // If the response data is a string, it also means the request was unsuccessful.
  if (
    (typeof response?.data === 'string' &&
      response?.data.includes('Sign in to the ALA')) ||
    response?.status === 403
  ) {
    await log('Cookie is invalid!');
    throw new Error('Cookie is invalid!');
  }

  return image;
}

async function getImagesForSpecies(species) {
  // Construct the API url
  const URL = `https://${
    args.dev ? 'profiles-dev' : 'profiles'
  }.ala.org.au/opus/${
    args.opusId || 'mangrovewatch'
  }/profile/${encodeURIComponent(
    species
  )}/images/paged?readonlyView=false&pageSize=80&startIndex=0`;

  let response;
  try {
    response = await axios.get(URL);
    return response.data.images
      .filter(({ type }) => type.name === 'PRIVATE')
      .map(({ imageId }) => imageId);
  } catch (error) {
    await log(
      `Could not retrieve images for '${species}' (${
        error?.response?.status || 'Unknown Error'
      })`
    );
  }

  return [];
}

async function bulkDelete(cookie) {
  // Load species CSV
  const species = await csv().fromFile('./data/mgwatch_species.csv');

  // Sequentially delete each image
  for (
    let speciesIndex = 0;
    speciesIndex <= species.length;
    speciesIndex += 1
  ) {
    const speciesName = species[speciesIndex]?.['Species Name'];
    if (speciesName) {
      await log(`Retrieving images for '${speciesName}'...`);
      const speciesImages = await getImagesForSpecies(speciesName);

      // c await log(`Deleting ${speciesImages.length} images...`);
      // await Promise.all(
      //   speciesImages.map((imageId) =>
      //     deleteImage(speciesName, imageId, cookie)
      //   )
      // );
      //  await log('Deleted!');

      for (
        let imageIndex = 0;
        imageIndex < speciesImages.length;
        imageIndex += 1
      ) {
        const image = speciesImages[imageIndex];
        await log(`Deleting image ${image} for '${speciesName}'`);
        await deleteImage(speciesName, image, cookie);
      }
    }
  }
}

(async () => {
  // Read the cookie from the cookie file
  const cookie = await fsp.readFile('./cookie.txt', 'utf8');

  // Start bulk deleting images
  await bulkDelete(cookie);
})();
