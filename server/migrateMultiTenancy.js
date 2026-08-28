/**
 * Migration script: Single-tenant → Multi-tenant
 * 
 * Run this ONCE to:
 * 1. Create User accounts for Narayan and Tanaya
 * 2. Create their Couple document
 * 3. Stamp all existing data with the coupleId
 * 4. Migrate spotify settings into the Couple
 */
require('dotenv').config();
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const User = require('./models/User');
const Couple = require('./models/Couple');
const Milestone = require('./models/Milestone');
const LoveNote = require('./models/LoveNote');
const Memory = require('./models/Memory');
const DreamLocation = require('./models/DreamLocation');

async function migrate() {
  console.log('✿ Starting multi-tenancy migration...\n');

  try {
    await mongoose.connect(process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/ourbloom');
    console.log('✿ Connected to MongoDB\n');

    // 1. Create Users (skip if already exist)
    let narayan = await User.findOne({ email: 'narayanphukan30@gmail.com' });
    if (!narayan) {
      narayan = new User({
        email: 'narayanphukan30@gmail.com',
        password: 'Narayan',
        name: 'Narayan',
        nicknameForPartner: 'Guxaini',
      });
      await narayan.save();
      console.log('✿ Created user: Narayan (narayanphukan30@gmail.com)');
    } else {
      console.log('✿ User Narayan already exists, skipping');
    }

    let tanaya = await User.findOne({ email: 'tanayaburagohain44@gmail.com' });
    if (!tanaya) {
      tanaya = new User({
        email: 'tanayaburagohain44@gmail.com',
        password: 'Tanaya',
        name: 'Tanaya',
        nicknameForPartner: 'Kuchupuchu',
      });
      await tanaya.save();
      console.log('✿ Created user: Tanaya (tanayaburagohain44@gmail.com)');
    } else {
      console.log('✿ User Tanaya already exists, skipping');
    }

    // 2. Create Couple
    let couple = await Couple.findOne({ slug: 'narayan-tanaya' });
    if (!couple) {
      // Try to get spotify track from old Settings collection
      let spotifyTrackId = '4O2N861eOnF9q8EtpH8IJu';
      try {
        const Settings = mongoose.model('Settings', new mongoose.Schema({ spotifyTrackId: String }));
        const settings = await Settings.findOne();
        if (settings && settings.spotifyTrackId) {
          spotifyTrackId = settings.spotifyTrackId;
          console.log(`✿ Migrated Spotify track ID: ${spotifyTrackId}`);
        }
      } catch (e) {
        // Settings collection might not exist, that's fine
      }

      couple = new Couple({
        slug: 'narayan-tanaya',
        user1: narayan._id,
        user2: tanaya._id,
        inviteCode: 'BLOOM-ORIG',
        startDate: new Date('2026-05-29'),
        startTime: '15:50',
        specialPhrase: 'Forever blooming together',
        spotifyTrackId,
      });
      await couple.save();
      console.log('✿ Created couple: narayan-tanaya');
    } else {
      console.log('✿ Couple narayan-tanaya already exists, skipping');
    }

    // 3. Update user coupleIds
    if (!narayan.coupleId || narayan.coupleId.toString() !== couple._id.toString()) {
      narayan.coupleId = couple._id;
      await User.updateOne({ _id: narayan._id }, { coupleId: couple._id });
      console.log('✿ Linked Narayan to couple');
    }

    if (!tanaya.coupleId || tanaya.coupleId.toString() !== couple._id.toString()) {
      tanaya.coupleId = couple._id;
      await User.updateOne({ _id: tanaya._id }, { coupleId: couple._id });
      console.log('✿ Linked Tanaya to couple');
    }

    // 4. Stamp all existing data with coupleId
    const coupleId = couple._id;

    const milestoneResult = await Milestone.updateMany(
      { coupleId: { $exists: false } },
      { $set: { coupleId } }
    );
    console.log(`✿ Stamped ${milestoneResult.modifiedCount} milestones with coupleId`);

    // Also stamp milestones that have coupleId: null
    const milestoneResult2 = await Milestone.updateMany(
      { coupleId: null },
      { $set: { coupleId } }
    );
    console.log(`✿ Stamped ${milestoneResult2.modifiedCount} milestones (null coupleId)`);

    const loveNoteResult = await LoveNote.updateMany(
      { coupleId: { $exists: false } },
      { $set: { coupleId } }
    );
    console.log(`✿ Stamped ${loveNoteResult.modifiedCount} love notes with coupleId`);

    const loveNoteResult2 = await LoveNote.updateMany(
      { coupleId: null },
      { $set: { coupleId } }
    );
    console.log(`✿ Stamped ${loveNoteResult2.modifiedCount} love notes (null coupleId)`);

    const memoryResult = await Memory.updateMany(
      { coupleId: { $exists: false } },
      { $set: { coupleId } }
    );
    console.log(`✿ Stamped ${memoryResult.modifiedCount} memories with coupleId`);

    const memoryResult2 = await Memory.updateMany(
      { coupleId: null },
      { $set: { coupleId } }
    );
    console.log(`✿ Stamped ${memoryResult2.modifiedCount} memories (null coupleId)`);

    const locationResult = await DreamLocation.updateMany(
      { coupleId: { $exists: false } },
      { $set: { coupleId } }
    );
    console.log(`✿ Stamped ${locationResult.modifiedCount} dream locations with coupleId`);

    const locationResult2 = await DreamLocation.updateMany(
      { coupleId: null },
      { $set: { coupleId } }
    );
    console.log(`✿ Stamped ${locationResult2.modifiedCount} dream locations (null coupleId)`);

    // Summary
    console.log('\n✿ ========== Migration Summary ==========');
    console.log(`✿ Users created: Narayan, Tanaya`);
    console.log(`✿ Couple: narayan-tanaya (invite: BLOOM-ORIG)`);
    console.log(`✿ Milestones stamped: ${milestoneResult.modifiedCount + milestoneResult2.modifiedCount}`);
    console.log(`✿ Love notes stamped: ${loveNoteResult.modifiedCount + loveNoteResult2.modifiedCount}`);
    console.log(`✿ Memories stamped: ${memoryResult.modifiedCount + memoryResult2.modifiedCount}`);
    console.log(`✿ Dream locations stamped: ${locationResult.modifiedCount + locationResult2.modifiedCount}`);
    console.log('✿ ========================================\n');
    console.log('✿ Migration complete! ✨');

    process.exit(0);
  } catch (err) {
    console.error('✿ Migration failed:', err);
    process.exit(1);
  }
}

migrate();
