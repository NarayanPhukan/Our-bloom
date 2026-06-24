const Milestone = require('./models/Milestone');

const defaultMilestones = [
  {
    day: 1,
    label: 'Day 01 — The First Hello',
    title: 'When Time Stood Still',
    body: "I remember the exact way the light hit the room when you walked in. It wasn't just a meeting; it was the start of a rhythm I never knew I was missing.",
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD53OUmN9cBOM5wW4Cph2_2Ws_2PI9e59jEh0DnRdcOX5E6MLd9d_3bBBUsFsXQYl0IqksVRT63LdwXWH62ul52LW3cPObgtCczSvbpkdzNQ7RiTGQ25j1SZ80BkuGf_TlwVyQFpX9gQ9ZF6x-j6Sbb7xQtxgl-0lzTreTpmYHixh7lOoHoUXO6AkbYUwNtrqLPdGTYJ80VWbSCZ6JvBAB6oFevduA12-CIYJURCoVpnlNvYWf-OBr4tENoe-61Nhd9fsZyY5H8LQ-e',
    icon: 'local_florist',
    iconFill: false,
    colorScheme: 'primary',
    aspectRatio: 'video',
  },
  {
    day: 7,
    label: 'Day 07 — The First Rain',
    title: 'Shelter in the Storm',
    body: "The unexpected downpour that forced us under that tiny green umbrella. We were soaked, but I've never felt more at home than I did standing next to you.",
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAAzOXi8j5A-xD_TZi-f9jOKaULPhefKaSsJlT9w3ZxbE9_B8FcQ08EcoaXQPneO-rMhMx0X-csHf5X1phwgrrmQLzLN3LINr_rbv4X7JMPssYF9GZLFGggP-mumltbB41Nh2eE57wKUIzd-cQ7ppg9A61JtsjIfEKV1J2eFSFMk77wjhmKdXtqZxn5C14LdYdfrZvEYa1VBi5MoofMR1czAUdzm4CjCqTVGizyjlmHqQ5QAadTHGN1t9d0e3gOXTHdKDBAG8PmUsSt',
    icon: 'water_drop',
    iconFill: false,
    colorScheme: 'secondary',
    aspectRatio: '4/5',
  },
  {
    day: 15,
    label: 'Day 15 — Middle of the Bloom',
    title: 'Quiet Understandings',
    body: 'By the second week, the silences between us became as comfortable as the conversations. We stopped performing and started simply being.',
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAIgbEettGtJ-BBUST7-fCYFiF4YLYYwq7-Qk-keWShqCYYNIpP-_1BIW3aburpU8-9zjHnXEb8GySO20nAh3rKGwQ1wvgwwRNIfM29iE-ivrvOnDQdIYLho-WcRPoK4a6sU06Z9z7NM_K5Xornt_MdcyPxal9wAD8p4QbCsevRk6CiJMLMG6uonvBwqh72RotDVi-9wb0e800oqV76c0zzBn9pl3jTmbJM8a9Q_fD_BhLFLDdVGq8uRAex3y9uivPt_MiyCnZ-pTHQ',
    icon: 'auto_awesome',
    iconFill: false,
    colorScheme: 'tertiary',
    aspectRatio: 'video',
  },
  {
    day: 30,
    label: 'Day 30 — Our First Month',
    title: 'A Garden in Progress',
    body: "Thirty days. Seven hundred and twenty hours. One beautiful, growing love. This is only the first chapter of a book I never want to finish reading.",
    imageUrl: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCuIgCc-RKYXTVqXTpfMYCBqHOhnXKCQNEk3iOVx__NoW24JH4qOnI7VUuMQRbBGbskC479xwGqFFW44DwR5d0-ppfUcgFG2aclAM0XpB1VdD-kwTqdI2vbS-qP3O0EuOD-Bln41GND83SUfwzVgnSts-wC6bjgLxYHowmwjhowhWCupGk9y0N1bnYP7Q6N8-gFIr8EO0z1lhxLxg2vPjzlaUvt3-YF1En0qkjQFU9WmJo5T8jTnHORh0jcZhv92TPV5a4K6xaBL1c0',
    icon: 'favorite',
    iconFill: true,
    colorScheme: 'primary',
    aspectRatio: 'square',
  },
];

async function seedDatabase() {
  try {
    const count = await Milestone.countDocuments();
    if (count === 0) {
      await Milestone.insertMany(defaultMilestones);
      console.log('✿ Database seeded with default milestones');
    } else {
      console.log(`✿ Database already has ${count} milestone(s), skipping seed`);
    }
  } catch (err) {
    console.error('Seed error:', err.message);
  }
}

module.exports = seedDatabase;
