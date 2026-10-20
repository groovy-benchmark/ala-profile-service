const fs = require('fs');
const fsp = require('fs/promises');

const FormData = require('form-data');
const csv = require('csvtojson');
const axios = require('axios').default;
const yargs = require('yargs/yargs');
const { hideBin } = require('yargs/helpers');

const LOG_FILE = `./load-${Date.now()}.log`;

const log = async (message, ...optionalParams) => {
  await fsp.appendFile(LOG_FILE, `${message}\n`);
  console.log(message, ...optionalParams);
};

// Parameter parsing & check
const parameters = ['dataResourceId'];
const args = yargs(hideBin(process.argv)).argv;
const dataDir = args.dataDir || './data';
const taxonFile = `${dataDir}/taxon.csv`;
const imageFile = `${dataDir}/images.csv`;
const imagesDir = args.imagesDir || "./images";
const rightsHolder = args.rightsHolder || 'MangroveWatch - NCDuke ©';
const opusId = args.opusId || 'mangrovewatch';
const env = args.dev ? 'profiles-dev' : 'profiles'
const mode = args.mode || "image-publish";
for (const param of parameters) {
  if (!args[param]) {
    log(`Missing '${param}' parameter!`);
    return;
  }
}

async function uploadImage(image, cookie) {
  // Create a new FormData Instance
  const form = new FormData();

  // Append form attributes
  form.append('rightsHolder', rightsHolder);
  form.append('dataResourceId', args.dataResourceId);
  form.append('creator', image['AUTHOR']);
  form.append('title', image['CAPTION']);
  form.append('rights', 'Fair use with acknowledgement of rights holder.');
  form.append(
    'licence',
    'Creative Commons Attribution-Noncommercial-Share Alike (International)'
  );

  // Append the file to the form
  form.append(
    'file',
    fs.createReadStream(`${imagesDir}/${image['IMAGE FILENAME']}`),
    image['IMAGE FILENAME']
  );

  // Construct the API url
  const URL = `https://${
    env
  }.ala.org.au/opus/${
    opusId
  }/profile/${encodeURIComponent(image['Species Name'])}/image/upload`;

  let response;
  log({
    ...form.getHeaders(),
    cookie,
  })
  try {
    response = await axios.post(URL, form, {
      headers: {
        ...form.getHeaders(),
        cookie,
      },
    });
  } catch (error) {
    if (error?.response?.status === 403) {
      await log('Cookie is invalid!');
      throw new Error('Cookie is invalid!');
    }

    await log(
      `Image upload failed (${
        error?.response?.status || 'Unknown Error'
      })\n${URL}`
    );
    await log(JSON.stringify(image, null, 2));
    if (error?.message) {
      await log(error.message);
    }
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

async function publishPrivateImages(image, cookie) {
  if (image['Species Name']) {
    try {
      let profile = await getProfile(image['Species Name'], cookie);

      if (profile) {
        updateImageSettings(profile);
        await updateProfile(image['Species Name'], profile, cookie);
      }
    } catch (error) {
      await log(error.message);
    }
  }
}

function updateImageSettings(profile) {
  let imageSettings = profile.imageSettings || [];
  profile.privateImages.forEach(image => {
    let imageSetting = imageSettings.find(setting => setting.imageId === image.imageId);
    if (!imageSetting) {
      profile.imageSettings.push({
        "imageId": image.imageId,
        "caption": null,
        "displayOption": null
      });
    }
  });
  return profile;
}

async function getProfile(speciesName, cookie){
  const URL = `https://${
      env
  }.ala.org.au/opus/${
      opusId
  }/profile/${encodeURIComponent(speciesName)}/json`;

  let response;
  try {
    response = await axios.get(URL, {
      headers: {
        cookie
      }
    });
  } catch (error) {
    if (error?.response?.status === 403) {
      await log('Cookie is invalid!');
      throw new Error('Cookie is invalid!');
    }

    await log(
        `get json for profile failed (${
            error?.response?.status || 'Unknown Error'
        })\n${URL}`
    );
    if (error?.message) {
      await log(error.message);
    }
  }

  // If the response data is a string, it also means the request was unsuccessful.
  if (
      (typeof response?.data === 'string') ||
      response?.status === 403
  ) {
    await log('Cookie is invalid!');
    throw new Error('Cookie is invalid!');
  }

  return response.data.profile;
}

async function updateProfile(speciesName, profile, cookie){
  const URL = `https://${
      env
  }.ala.org.au/opus/${
      opusId
  }/profile/${encodeURIComponent(speciesName)}/update`;

  let response;
  try {
    response = await axios.post(URL, profile, {
      headers: {
        cookie
      }
    });
  } catch (error) {
    if (error?.response?.status === 403) {
      await log('Cookie is invalid!');
      throw new Error('Cookie is invalid!');
    }

    await log(
        `get json for profile failed (${
            error?.response?.status || 'Unknown Error'
        })\n${URL}`
    );
    if (error?.message) {
      await log(error.message);
    }
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

  return response.data;
}

async function bulkUpload(cookie) {
  // Load species & images CSV
  let [species, images] = await Promise.all([
    await csv().fromFile(taxonFile),
    await csv().fromFile(imageFile),
  ]);

  // Sort the species by the length of the code so that names aren't preliminarily matched
  // Ac_ebr,Acanthus ebracteatus
  // Ac_ebr-ebr,Acanthus ebracteatus subsp. ebracteatus
  species = species.sort(
    (a, b) => b['Taxa Code'].length - a['Taxa Code'].length
  );

  // Map each image to include the species name based on the taxon code
  let mapped = images.map((image) => ({
    ...image,
    'Species Name': species.find((species) =>
      image['IMAGE FILENAME']
        .toLowerCase()
        .startsWith(species['Taxa Code'].toLowerCase())
    )?.['Species Name'],
  }));

  // Sequentially upload each image
  for (
    let uploadIndex = args.start || 0;
    uploadIndex < mapped.length;
    uploadIndex++
  ) {
    const image = mapped[uploadIndex];

    await log(
      `Uploading image ${uploadIndex + 1}/${mapped.length}... (${
        image['Species Name']
      }, ${image['IMAGE FILENAME']})`
    );

    switch (mode) {
      case "image":
        await uploadImage(image, cookie, imagesDir);
        break;
      case "publish":
        await publishPrivateImages(image, cookie);
        break;
      case "image-publish":
        await uploadImage(image, cookie, imagesDir);
        await publishPrivateImages(image, cookie);
        break;
    }
  }
}

(async () => {
  // Read the cookie from the cookie file
  const cookie = await fsp.readFile('./cookie.txt', 'utf8');

  // Start bulk loading the images
  await bulkUpload(cookie);
})();
