const cron = require('node-cron');
const transporter = require('../config/email');
const { GoogleGenerativeAI } = require('@google/generative-ai');

function getMonthAnniversaryNumber() {
  const startDate = new Date('2026-05-29T00:00:00');
  const currentDate = new Date();
  
  let months = (currentDate.getFullYear() - startDate.getFullYear()) * 12;
  months -= startDate.getMonth();
  months += currentDate.getMonth();
  
  return months;
}

function getOrdinalNum(n) {
  return n + (['st', 'nd', 'rd'][((n + 90) % 100 - 10) % 10 - 1] || 'th');
}

async function generateAnniversaryMessage(anniversaryNumber) {
  try {
    const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
    const model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });

    const prompt = `Write a beautiful, romantic, and poetic paragraph (around 3-4 sentences) for my girlfriend Tanaya, wishing her a happy ${anniversaryNumber} month anniversary. We started our relationship on May 29, 2026. My name is Narayan. She loves Lily flowers. She affectionately calls me "kuchupuchu", and I call her "Tiku Guxaini". Mention how much I adore her singing like a baby during our calls, our dreams of traveling to get a tattoo in Thailand, and going to Bali for our honeymoon. Most importantly, express how much she has healed me and made me believe that true love exists. Incorporate these beautiful personal details into the message gracefully. Make it sound genuine, heartfelt, and deeply loving, and sprinkle in some romantic emojis. Do not include any subject lines or placeholders, just the text.`;

    const result = await model.generateContent(prompt);
    const response = await result.response;
    return response.text();
  } catch (error) {
    console.error('Error generating email content with Gemini:', error);
    // Fallback message in case Gemini fails
    return `Happy ${anniversaryNumber} month anniversary, my love! Every day with you is a walk through a beautiful garden, and I am so grateful for our journey together. Here is to forever blooming together. Love, Narayan.`;
  }
}

async function sendAnniversaryEmail() {
  try {
    const monthNum = getMonthAnniversaryNumber();
    if (monthNum <= 0) return; // Don't send if it's before or exactly on the start date somehow
    
    const ordinalMonth = getOrdinalNum(monthNum);
    const message = await generateAnniversaryMessage(ordinalMonth);

    const mailOptions = {
      from: process.env.EMAIL_USER,
      to: 'tanayaburagohain2244@gmail.com',
      subject: `Happy ${ordinalMonth} Month Anniversary, My Love ❤️`,
      text: message,
    };

    const info = await transporter.sendMail(mailOptions);
    console.log('Anniversary email sent successfully:', info.messageId);
  } catch (error) {
    console.error('Error sending anniversary email:', error);
  }
}

// The cron job runs at 00:00 on the 29th of every month.
// However, since the user mentioned they will hit the server on 28th 23:57,
// we will just keep the schedule as '0 0 29 * *' (Midnight on 29th).
// As long as the server is running at that time, it will trigger.
function initAnniversaryEmailJob() {
  console.log('Initializing Anniversary Email Cron Job (runs at 00:00 on the 29th)');
  
  cron.schedule('0 0 29 * *', () => {
    console.log('Cron triggered: Sending monthly anniversary email...');
    sendAnniversaryEmail();
  });
}

module.exports = { initAnniversaryEmailJob, sendAnniversaryEmail };
